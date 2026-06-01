import urllib.request
import urllib.error
import json

url = "https://faceapi.mxface.ai/api/v3/face/verify"
headers = {
    "Subscriptionkey": "Y7y4ZGKXXyoBrYotfI-zclVIeFRvy5399",
    "Content-Type": "application/json"
}
data = {
    "encoded_image1": "R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==",
    "encoded_image2": "R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="
}

req = urllib.request.Request(url, data=json.dumps(data).encode(), headers=headers)
try:
    response = urllib.request.urlopen(req)
    print(response.read().decode())
except urllib.error.HTTPError as e:
    print(f"Error: {e.code}")
    print(e.read().decode())
