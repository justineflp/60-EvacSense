import React, { useState } from 'react';
import LoginForm from '../components/LoginForm';
import { Zap } from 'lucide-react';
import { API_BASE_URL } from '../config';

export default function WebLoginPage({ onLoginSuccess, navigate }) {
  const [view, setView] = useState('login'); // login | register | sso_select
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // Registration Fields
  const [regRole, setRegRole] = useState('Student'); // Student | Teacher | Drill Coordinator
  const [regName, setRegName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regId, setRegId] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);


  const handlePasswordLogin = async (email, password) => {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });
      const data = await response.json();
      
      const getErrMsg = (msg, errs, fallback) => {
        if (typeof msg === 'string') return msg;
        if (Array.isArray(errs) && errs.length > 0) {
          const first = errs[0];
          if (typeof first === 'string') return first;
          if (typeof first === 'object' && first !== null) return first.message || first.error || JSON.stringify(first);
        }
        if (typeof msg === 'object' && msg !== null) return msg.message || msg.error || JSON.stringify(msg);
        return fallback;
      };

      if (response.ok && data.status === 'success') {
        if (data.user.role === 'Student') {
          setError('Access Denied: Student accounts must use the EvacSense Mobile App.');
          return;
        }
        onLoginSuccess(data.session.token, data.user);
      } else {
        setError(getErrMsg(data.message, data.errors, 'Authentication failed.'));
      }
    } catch (err) {
      setError('Connection to EvacSense authorization server failed. Make sure the backend is running.');
    } finally {
      setLoading(false);
    }
  };



  const handleRegistrationSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccessMessage('');

    if (!regName || !regEmail || !regId || !regPassword) {
      setError('Please fill out all fields.');
      setLoading(false);
      return;
    }

    try {
      let endpoint = '';
      let bodyData = {};

      if (regRole === 'Student') {
        endpoint = `${API_BASE_URL}/api/auth/register/student`;
        bodyData = {
          name: regName,
          email: regEmail,
          studentId: regId,
          password: regPassword,
          deviceId: 'DEVICE-' + Math.floor(1000 + Math.random() * 9000)
        };
      } else {
        endpoint = `${API_BASE_URL}/api/auth/register/staff`;
        bodyData = {
          name: regName,
          email: regEmail,
          employeeId: regId,
          password: regPassword,
          role: regRole,
          deviceId: 'DEVICE-' + Math.floor(1000 + Math.random() * 9000)
        };
      }

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(bodyData)
      });
      const data = await response.json();

      if (response.ok && (data.status === 'success' || data.status === 'pending')) {
        setSuccessMessage(typeof data.message === 'string' ? data.message : 'Registration successful.');
        // Clear fields
        setRegName('');
        setRegEmail('');
        setRegId('');
        setRegPassword('');
      } else {
        const rawErr = data.message || data.errors?.[0];
        setError(typeof rawErr === 'string' ? rawErr : (typeof rawErr === 'object' && rawErr !== null ? rawErr.message || JSON.stringify(rawErr) : 'Registration failed.'));
      }

    } catch (err) {
      setError('Connection to EvacSense authorization server failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div className="glass-panel" style={{
        width: '100%',
        maxWidth: '480px',
        padding: '2.5rem',
        textAlign: 'center',
        animation: 'fadeIn 0.6s ease-out'
      }}>
        
        {/* Logo and Brand Header */}
        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{
            width: '56px',
            height: '56px',
            borderRadius: '16px',
            background: 'linear-gradient(135deg, #991b1b, #fbbf24)',
            margin: '0 auto 0.75rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '1.5rem',
            fontWeight: '800',
            color: '#ffffff',
            boxShadow: '0 8px 24px rgba(153, 27, 27, 0.35)',
            fontFamily: 'Outfit'
          }}><Zap size={32} /></div>
          <h1 className="brand-title" style={{ fontSize: '2rem' }}>EvacSense</h1>
          <p className="brand-subtitle" style={{ fontSize: '0.75rem' }}>CIT-U Earthquake Drill Suite</p>
        </div>

        {/* 1. Login View */}
        {view === 'login' && (
          <>
            <LoginForm 
              onSubmit={handlePasswordLogin} 
              loading={loading} 
              errorMessage={error} 
            />



            <button 
              type="button" 
              className="btn btn-secondary"
              onClick={() => { setView('register'); setError(''); setSuccessMessage(''); }}
              disabled={loading}
              style={{ width: '100%', border: '1px dashed var(--accent-gold)', color: 'var(--accent-gold)' }}
            >
              Create Institutional Account
            </button>

            <div style={{ marginTop: '1.25rem' }}>
              <button 
                type="button"
                onClick={() => navigate('recovery')}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-secondary)',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                  textDecoration: 'underline'
                }}
              >
                Forgot Password or Account Recovery?
              </button>
            </div>
          </>
        )}

        {/* 2. Registration View */}
        {view === 'register' && (
          <div style={{ animation: 'fadeIn 0.3s ease-out', textAlign: 'left' }}>
            <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.25rem', marginBottom: '0.25rem', textAlign: 'center' }}>Account Registration</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '1.25rem', textAlign: 'center' }}>Submit institutional credentials to join EvacSense</p>

            {error && (
              <div style={{
                background: 'rgba(239, 68, 68, 0.12)',
                border: '1px solid rgba(239, 68, 68, 0.25)',
                color: '#f87171',
                padding: '0.75rem 1rem',
                borderRadius: '10px',
                fontSize: '0.875rem',
                marginBottom: '1rem'
              }}>{error}</div>
            )}

            {successMessage && (
              <div style={{
                background: 'rgba(16, 185, 129, 0.12)',
                border: '1px solid rgba(16, 185, 129, 0.25)',
                color: '#34d399',
                padding: '0.75rem 1rem',
                borderRadius: '10px',
                fontSize: '0.875rem',
                marginBottom: '1rem'
              }}>{successMessage}</div>
            )}

            <form onSubmit={handleRegistrationSubmit}>
              <div className="form-group">
                <label className="form-label" htmlFor="reg-role">Role</label>
                <select
                  id="reg-role"
                  className="form-input"
                  value={regRole}
                  onChange={(e) => setRegRole(e.target.value)}
                  style={{ background: 'rgba(15,23,42,0.85)', color: '#ffffff' }}
                >
                  <option value="Student">Student (Auto-Activated)</option>
                  <option value="Teacher">Teacher / Staff (Requires Admin Approval)</option>
                  <option value="Drill Coordinator">Drill Coordinator (Requires Admin Approval)</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-name">Full Name</label>
                <input
                  id="reg-name"
                  type="text"
                  className="form-input"
                  placeholder="e.g. Maria Santos"
                  value={regName}
                  onChange={(e) => setRegName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-email">Institutional Email</label>
                <input
                  id="reg-email"
                  type="email"
                  className="form-input"
                  placeholder={regRole === 'Student' ? 'username@student.cit.edu' : 'username@cit.edu'}
                  value={regEmail}
                  onChange={(e) => setRegEmail(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-id">
                  {regRole === 'Student' ? 'Student ID Number' : 'Employee ID Number'}
                </label>
                <input
                  id="reg-id"
                  type="text"
                  className="form-input"
                  placeholder="e.g. USR-006"
                  value={regId}
                  onChange={(e) => setRegId(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-password">Password</label>
                <div style={{ position: 'relative' }}>
                  <input
                    id="reg-password"
                    type={showRegPassword ? "text" : "password"}
                    className="form-input"
                    placeholder="••••••••••••"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    required
                    style={{ paddingRight: '40px' }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowRegPassword(!showRegPassword)}
                    style={{
                      position: 'absolute',
                      right: '10px',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      color: 'var(--text-muted)',
                      fontSize: '1.2rem',
                      padding: 0
                    }}
                  >
                    {showRegPassword ? (
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                        <line x1="1" y1="1" x2="23" y2="23"></line>
                      </svg>
                    ) : (
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                        <circle cx="12" cy="12" r="3"></circle>
                      </svg>
                    )}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary"
                disabled={loading}
                style={{ marginBottom: '0.75rem' }}
              >
                {loading ? 'Submitting Registration...' : 'Register Secure Profile'}
              </button>

              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => { setView('login'); setError(''); setSuccessMessage(''); }}
                disabled={loading}
                style={{ width: '100%' }}
              >
                Back to Login
              </button>
            </form>
          </div>
        )}


      </div>
    </div>
  );
}
