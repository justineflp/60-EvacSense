import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";

const firebaseConfig = {
  apiKey: "AIzaSyCSMF8Q_rLe7oV4m2js3Gr9SGECKV9yDFc",
  authDomain: "evacsense-64cc2.firebaseapp.com",
  projectId: "evacsense-64cc2",
  storageBucket: "evacsense-64cc2.firebasestorage.app",
  messagingSenderId: "476467503204",
  appId: "1:476467503204:web:dab891e97f9b903576ff71",
  measurementId: "G-XN81S47CJP"
};

const app = initializeApp(firebaseConfig);
export const analytics = typeof window !== 'undefined' ? getAnalytics(app) : null;
export default app;
