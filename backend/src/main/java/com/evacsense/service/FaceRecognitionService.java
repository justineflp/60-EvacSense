package com.evacsense.service;

import com.evacsense.model.User;
import com.evacsense.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FaceRecognitionService {

    @Autowired
    private UserRepository userRepository;

    // Transient in-memory retry store: userId -> failed attempts count
    private final Map<String, Integer> retryAttemptsStore = new ConcurrentHashMap<>();

    public static class FaceVerifyResult {
        public boolean success;
        public float confidence;
        public int attemptsRemaining;
        public String message;

        public FaceVerifyResult(boolean success, float confidence, int attemptsRemaining, String message) {
            this.success = success;
            this.confidence = confidence;
            this.attemptsRemaining = attemptsRemaining;
            this.message = message;
        }
    }

    @org.springframework.beans.factory.annotation.Value("${mxface.api.key:Y7y4ZGKXXyoBrYotfI-zclVIeFRvy5399}")
    private String mxFaceApiKey;

    @org.springframework.beans.factory.annotation.Value("${mxface.api.url:https://faceapi.mxface.ai/api/v3/face/verify}")
    private String mxFaceApiUrl;

    public FaceVerifyResult verifyFace(String userId, String livePhotoBase64) {
        int attempts = retryAttemptsStore.getOrDefault(userId, 0);

        if (attempts >= 3) {
            return new FaceVerifyResult(
                    false,
                    0.0f,
                    0,
                    "Maximum biometric attempts exceeded. Account flagged for manual marshal verification.");
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || userOpt.get().getPhotoBase64() == null || userOpt.get().getPhotoBase64().isEmpty()) {
            return new FaceVerifyResult(
                    false,
                    0.0f,
                    3,
                    "Verification failed: No registered baseline student photo found. Please register your face in the Dashboard first.");
        }

        String storedPhotoBase64 = userOpt.get().getPhotoBase64();

        // Strip "data:image/...;base64," prefixes if present
        if (storedPhotoBase64.contains(",")) {
            storedPhotoBase64 = storedPhotoBase64.split(",")[1];
        }
        if (livePhotoBase64 != null && livePhotoBase64.contains(",")) {
            livePhotoBase64 = livePhotoBase64.split(",")[1];
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Subscriptionkey", mxFaceApiKey);

            // Create JSON payload matching MxFace documentation
            Map<String, String> payloadMap = new java.util.HashMap<>();
            payloadMap.put("encoded_image1", storedPhotoBase64);
            payloadMap.put("encoded_image2", livePhotoBase64);

            org.springframework.http.HttpEntity<Map<String, String>> request = new org.springframework.http.HttpEntity<>(payloadMap, headers);

            org.springframework.http.ResponseEntity<Map> apiResponse = restTemplate.postForEntity(mxFaceApiUrl, request, Map.class);
            Map<String, Object> responseBody = apiResponse.getBody();
            
            if (responseBody != null && responseBody.containsKey("matchedFaces")) {
                java.util.List<Map<String, Object>> matchedFaces = (java.util.List<Map<String, Object>>) responseBody.get("matchedFaces");
                if (matchedFaces != null && !matchedFaces.isEmpty()) {
                    Map<String, Object> bestMatch = matchedFaces.get(0);
                    Object matchResultObj = bestMatch.get("matchResult");
                    Integer matchResult = (matchResultObj instanceof Integer) ? (Integer) matchResultObj : Integer.parseInt(matchResultObj.toString());
                    Double apiConfidence = (bestMatch.get("confidence") instanceof Double) ? (Double) bestMatch.get("confidence") : Double.parseDouble(bestMatch.get("confidence").toString());
                    
                    boolean isMatch = false;
                    if (matchResult != null && matchResult == 1) {
                        isMatch = true;
                    } else if (apiConfidence != null) {
                        if (apiConfidence <= 1.0 && apiConfidence >= 0.60) {
                            isMatch = true;
                        } else if (apiConfidence > 1.0 && apiConfidence >= 60.0) {
                            isMatch = true;
                        }
                    }
                    
                    if (isMatch) {
                        float finalConfidence = apiConfidence != null ? apiConfidence.floatValue() : 0.95f;
                        if (finalConfidence <= 1.0f) {
                            finalConfidence *= 100.0f; // Convert 0.99 to 99.0
                        }
                        retryAttemptsStore.remove(userId);
                        return new FaceVerifyResult(true, finalConfidence, 3, "Facial Verification Successful!");
                    } else {
                        attempts += 1;
                        retryAttemptsStore.put(userId, attempts);
                        return new FaceVerifyResult(false, 0.0f, Math.max(0, 3 - attempts), "Face did not match registered photo (Confidence too low). Please try again.");
                    }
                } else {
                    attempts += 1;
                    retryAttemptsStore.put(userId, attempts);
                    return new FaceVerifyResult(false, 0.0f, Math.max(0, 3 - attempts), "No faces detected in the photo. Please ensure good lighting and try again.");
                }
            } else {
                attempts += 1;
                retryAttemptsStore.put(userId, attempts);
                return new FaceVerifyResult(false, 0.0f, Math.max(0, 3 - attempts), "Facial verification failed: No clear face detected or lighting is too dark. Please try again.");
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("=== MXFACE HTTP ERROR ===");
            String errorBody = e.getResponseBodyAsString();
            System.err.println(errorBody);
            System.err.println("=========================");
            
            attempts += 1;
            retryAttemptsStore.put(userId, attempts);

            String errorMessage = "Facial recognition error. Please try again.";
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> errorMap = mapper.readValue(errorBody, Map.class);
                if (errorMap.containsKey("errorMessage")) {
                    errorMessage = (String) errorMap.get("errorMessage");
                } else if (errorMap.containsKey("error")) {
                    errorMessage = (String) errorMap.get("error");
                }
            } catch (Exception ex) {}

            return new FaceVerifyResult(false, 0.0f, Math.max(0, 3 - attempts), errorMessage);
        } catch (Exception e) {
            e.printStackTrace();
            attempts += 1;
            retryAttemptsStore.put(userId, attempts);
            return new FaceVerifyResult(false, 0.0f, Math.max(0, 3 - attempts), "Failed to connect to MxFace API: " + e.getMessage());
        }
    }

    public void resetRetryAttempts(String userId) {
        retryAttemptsStore.remove(userId);
    }

    public void resetAllRetryAttempts() {
        retryAttemptsStore.clear();
    }
}
