import React, { useEffect, useState } from 'react';
import { Shield, AlertTriangle, AlertCircle, Map as MapIcon, RefreshCw, Printer, Moon, PlayCircle, StopCircle, MapPin, BarChart2, ClipboardList, Building, CheckCircle, CheckCircle2, Link, Download } from 'lucide-react';
import { NGE_FLOOR_LAYOUTS, NGE_FLOOR_TITLES } from '../ngeFloorLayouts';
import { API_BASE_URL } from '../config';

export default function Dashboard({ user, token, onLogout }) {
  const [usersList, setUsersList] = useState([]);
  const [pendingRequests, setPendingRequests] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [loadingRequests, setLoadingRequests] = useState(false);
  
  // Module 2 states (Drills & Occupancy)
  const [activeDrill, setActiveDrill] = useState(null);
  const [roomsOccupancy, setRoomsOccupancy] = useState([]);
  const [unverifiedList, setUnverifiedList] = useState([]);
  const [totalParticipants, setTotalParticipants] = useState(0);
  const [, setVerifiedCount] = useState(0);
  const [unverifiedCount, setUnverifiedCount] = useState(0);
  const [loadingDrill, setLoadingDrill] = useState(false);

  // Module 4 & 5 live states
  const [arrivedCount, setArrivedCount] = useState(0);
  const [distressCount, setDistressCount] = useState(0);
  const [distressList, setDistressList] = useState([]);
  const [missingList, setMissingList] = useState([]);
  const [floorClearances, setFloorClearances] = useState([]);
  const [drillsList, setDrillsList] = useState([]);
  const [selectedReportId, setSelectedReportId] = useState('');
  const [reportData, setReportData] = useState(null);
  const [loadingReport, setLoadingReport] = useState(false);

  // Admin/Coordinator Operations Messages
  const [actionMessage, setActionMessage] = useState('');
  const [actionError, setActionError] = useState('');

  // Rejection Reason Prompt States
  const [rejectingId, setRejectingId] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  // activeTab State for coordinator panels
  const [activeTab, setActiveTab] = useState('occupancy'); // occupancy | navigation

  // Module 3 Graph States
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [, setLoadingGraph] = useState(false);
  const [selectedOrigin, setSelectedOrigin] = useState('');
  const [testPath, setTestPath] = useState([]);
  const [testInstructions, setTestInstructions] = useState([]);
  const [errorPath, setErrorPath] = useState('');
  const [selectedFloor, setSelectedFloor] = useState(1);

  // Add Node form states
  const [newNodeId, setNewNodeId] = useState('');
  const [newNodeName, setNewNodeName] = useState('');
  const [newNodeFloor, setNewNodeFloor] = useState('4');
  const [newNodeX, setNewNodeX] = useState('0');
  const [newNodeY, setNewNodeY] = useState('0');

  // Add Edge form states
  const [newEdgeFrom, setNewEdgeFrom] = useState('');
  const [newEdgeTo, setNewEdgeTo] = useState('');
  const [newEdgeWeight, setNewEdgeWeight] = useState('5.0');

  // Fetch Graph (nodes & edges)
  const fetchGraph = async () => {
    setLoadingGraph(true);
    try {
      const nodesRes = await fetch(`${API_BASE_URL}/api/nav/nodes`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const nodesData = await nodesRes.json();
      
      const edgesRes = await fetch(`${API_BASE_URL}/api/nav/edges`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const edgesData = await edgesRes.json();

      if (nodesRes.ok && edgesRes.ok) {
        setNodes(nodesData.nodes || []);
        setEdges(edgesData.edges || []);
      }
    } catch (err) {
      console.error("Failed to load map graph data:", err);
    } finally {
      setLoadingGraph(false);
    }
  };

  // Dijkstra test path finding
  const calculateTestRoute = async (originId) => {
    if (!originId) return;
    setTestPath([]);
    setTestInstructions([]);
    setErrorPath('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/nav/route?origin=${originId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setTestPath(data.route.path || []);
        setTestInstructions(data.route.instructions || []);
      } else {
        setErrorPath(data.message || 'No clear path found. All exits are blocked!');
      }
    } catch (err) {
      setErrorPath('Connection to pathfinding service failed.');
    }
  };

  // Toggle path blockage (link collapse/fire)
  const handleToggleBlockage = async (edgeId, currentStatus) => {
    setActionMessage('');
    setActionError('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/nav/block`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ edgeId, isBlocked: !currentStatus })
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setActionMessage(data.message);
        fetchGraph();
        if (selectedOrigin) {
          calculateTestRoute(selectedOrigin);
        }
      } else {
        setActionError(data.message || 'Failed to update blockage.');
      }
    } catch (err) {
      setActionError('Link blockage update failed.');
    }
  };

  // Create edge connection
  const handleAddEdge = async (e) => {
    e.preventDefault();
    if (!newEdgeFrom || !newEdgeTo || newEdgeFrom === newEdgeTo) return;
    setActionMessage('');
    setActionError('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/nav/edges`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          fromNodeId: newEdgeFrom,
          toNodeId: newEdgeTo,
          weight: parseFloat(newEdgeWeight) || 5.0
        })
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setActionMessage(data.message);
        fetchGraph();
        setNewEdgeWeight('5.0');
      } else {
        setActionError(data.message || 'Failed to create map link.');
      }
    } catch (err) {
      setActionError('Link connection creation failed.');
    }
  };

  // Create new node
  const handleAddNode = async (e) => {
    e.preventDefault();
    if (!newNodeId || !newNodeName || !newNodeFloor) return;
    setActionMessage('');
    setActionError('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/nav/nodes`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          id: newNodeId,
          name: newNodeName,
          floor: parseInt(newNodeFloor),
          building: 'College of Computer Studies',
          xMin: parseFloat(newNodeX) || 0,
          xMax: (parseFloat(newNodeX) || 0) + 10,
          yMin: parseFloat(newNodeY) || 0,
          yMax: (parseFloat(newNodeY) || 0) + 10
        })
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setActionMessage(data.message);
        fetchGraph();
        setNewNodeId('');
        setNewNodeName('');
        setNewNodeFloor('4');
        setNewNodeX('0');
        setNewNodeY('0');
      } else {
        setActionError(data.message || 'Failed to create node.');
      }
    } catch (err) {
      setActionError('Node creation failed.');
    }
  };

  // Sync users database (Admin only)
  const fetchUsers = async () => {
    if (user.role !== 'System Admin') return;
    setLoadingUsers(true);
    setActionError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/users`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setUsersList(data.users);
      } else {
        setActionError(data.message || 'Could not fetch user registry.');
      }
    } catch (err) {
      setActionError('Failed to sync with user database.');
    } finally {
      setLoadingUsers(false);
    }
  };

  // Sync pending approvals requests (Admin only)
  const fetchPendingRequests = async () => {
    if (user.role !== 'System Admin') return;
    setLoadingRequests(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/requests`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setPendingRequests(data.requests);
      }
    } catch (err) {
      console.error('Failed to sync pending registrations:', err);
    } finally {
      setLoadingRequests(false);
    }
  };

  // Fetch active drill & occupancy headcounts (Admin & Coordinator & Teacher)
  const fetchDrillState = async () => {
    if (user.role === 'Student') return;
    setLoadingDrill(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/presence/occupancy`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActiveDrill(data.activeDrill);
        setRoomsOccupancy(data.rooms || []);
        setUnverifiedList(data.unverifiedList || []);
        setTotalParticipants(data.totalParticipants || 0);
        setVerifiedCount(data.verifiedCount || 0);
        setUnverifiedCount(data.unverifiedCount || 0);
        setArrivedCount(data.arrivedCount || 0);
        setDistressCount(data.distressCount || 0);
        setDistressList(data.distressList || []);
        setMissingList(data.missingList || []);
        setFloorClearances(data.floorClearances || []);
      }
    } catch (err) {
      console.error('Failed to sync live drill occupancy state:', err);
    } finally {
      setLoadingDrill(false);
    }
  };

  const syncAdminData = () => {
    fetchUsers();
    fetchPendingRequests();
  };

  // Play distress emergency sawtooth audio tone
  const playDistressSound = () => {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(880, ctx.currentTime);
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.3);
    } catch (err) {}
  };

  // Manual Safety Marshal Clear Override
  const handleMarshalClear = async (targetUserId) => {
    setActionMessage('');
    setActionError('');
    try {
      const res = await fetch(`${API_BASE_URL}/api/checkin/clear/${targetUserId}`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setActionMessage(data.message);
        fetchDrillState();
      } else {
        setActionError(data.message || 'Marshal override failed.');
      }
    } catch (err) {
      setActionError('Connection to override API failed.');
    }
  };

  // Module 5 Report Generation Helpers
  const fetchDrillsList = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/reports/list`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setDrillsList(data.drills || []);
      }
    } catch (err) {
      console.error("Failed to load drills list:", err);
    }
  };

  const loadReportDetails = async (drillId) => {
    if (!drillId) return;
    setLoadingReport(true);
    setReportData(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/reports/report/${drillId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        setReportData(data.report);
      }
    } catch (err) {
      console.error("Failed to load report analytics:", err);
    } finally {
      setLoadingReport(false);
    }
  };

  useEffect(() => {
    if (user.role === 'System Admin') {
      syncAdminData();
    }
    
    // Connect all authenticated roles (including Student) to real-time events stream
    const sseSource = new EventSource(`${API_BASE_URL}/api/presence/realtime`);

    if (user.role === 'System Admin' || user.role === 'Drill Coordinator' || user.role === 'Teacher') {
      fetchGraph();
      fetchDrillsList();
    }

    sseSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        if (data.type === 'route_recompute' || (data.status === 'success' && data.edge)) {
          // Live route/blockage recalculation update broadcasted via SSE!
          if (user.role === 'System Admin' || user.role === 'Drill Coordinator' || user.role === 'Teacher') {
            fetchGraph();
          }
          return;
        }

        // If it is a partial event that is NOT a dashboard sync, ignore it to prevent state wipe
        if (data.type && data.type !== 'dashboard_sync' && !data.rooms) {
          console.log('[SSE] Received partial SSE update, ignoring to prevent state wipe:', data);
          return;
        }
        
        setActiveDrill(data.activeDrill);
        setRoomsOccupancy(data.rooms || []);
        setUnverifiedList(data.unverifiedList || []);
        setTotalParticipants(data.totalParticipants || 0);
        setVerifiedCount(data.verifiedCount || 0);
        setUnverifiedCount(data.unverifiedCount || 0);
        setArrivedCount(data.arrivedCount || 0);
        
        if (data.distressCount > distressCount) {
          playDistressSound();
        }
        setDistressCount(data.distressCount || 0);
        setDistressList(data.distressList || []);
        setMissingList(data.missingList || []);
        setFloorClearances(data.floorClearances || []);
      } catch (err) {
        console.error('[SSE REAL-TIME ERROR] Parsing pushed frame failed:', err);
      }
    };

    sseSource.onerror = (err) => {
      console.warn('[SSE CONNECTION WARNING] Reconnecting real-time stream...');
    };

    return () => {
      sseSource.close();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.role, token]);

  const handleRoleChange = async (userId, newRole) => {
    setActionMessage('');
    setActionError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/users/${userId}/role`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ role: newRole })
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActionMessage(data.message);
        syncAdminData();
      } else {
        setActionError(data.message || data.errors?.[0] || 'Role update rejected.');
      }
    } catch (err) {
      setActionError('Role assignment failed.');
    }
  };

  const handleApproveRequest = async (requestId) => {
    setActionMessage('');
    setActionError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/requests/${requestId}/approve`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActionMessage(data.message);
        syncAdminData();
      } else {
        setActionError(data.message || data.errors?.[0] || 'Approval failed.');
      }
    } catch (err) {
      setActionError('Request approval failed.');
    }
  };

  const handleRejectRequestSubmit = async (e) => {
    e.preventDefault();
    if (!rejectionReason.trim()) {
      setActionError('A rejection reason is required.');
      return;
    }
    setActionMessage('');
    setActionError('');

    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/requests/${rejectingId}/reject`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ reason: rejectionReason })
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActionMessage(data.message);
        setRejectingId(null);
        setRejectionReason('');
        syncAdminData();
      } else {
        setActionError(data.message || data.errors?.[0] || 'Rejection failed.');
      }
    } catch (err) {
      setActionError('Request rejection failed.');
    }
  };

  const handleUnlockAccount = async (userId) => {
    setActionMessage('');
    setActionError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/unlock`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        }
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActionMessage(data.message);
        syncAdminData();
      } else {
        setActionError(data.message || data.errors?.[0] || 'Unlock failed.');
      }
    } catch (err) {
      setActionError('Account unlock request failed.');
    }
  };

  // Module 2: Start drill session trigger
  const handleStartDrill = async () => {
    setActionMessage('');
    setActionError('');
    setLoadingDrill(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/drill/start`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ name: 'CIT-U Annual Earthquake Drill ' + new Date().getFullYear() })
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActionMessage(data.message);
        fetchDrillState();
      } else {
        setActionError(data.message || 'Failed to activate drill session.');
      }
    } catch (err) {
      setActionError('Drill initialization failed.');
    } finally {
      setLoadingDrill(false);
    }
  };

  // Module 2: Conclude active drill session trigger
  const handleConcludeDrill = async () => {
    setActionMessage('');
    setActionError('');
    setLoadingDrill(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/drill/conclude`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const data = await response.json();
      if (response.ok && data.status === 'success') {
        setActionMessage(data.message);
        fetchDrillState();
      } else {
        setActionError(data.message || 'Failed to conclude drill session.');
      }
    } catch (err) {
      setActionError('Drill conclusion failed.');
    } finally {
      setLoadingDrill(false);
    }
  };

  const handleLogoutClick = async () => {
    try {
      await fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
    } catch (e) {}
    onLogout();
  };

  const renderBadge = (role) => {
    switch (role) {
      case 'Student': return <span className="badge badge-student">Student</span>;
      case 'Teacher': return <span className="badge badge-teacher">Teacher/Staff</span>;
      case 'Drill Coordinator': return <span className="badge badge-coordinator">Drill Coordinator</span>;
      case 'System Admin': return <span className="badge badge-admin">System Admin</span>;
      default: return <span className="badge">{role}</span>;
    }
  };

  return (
    <div style={{ minHeight: '100vh', padding: '3rem 2rem', position: 'relative' }}>
      
      {/* Top Banner Navigation */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '2.5rem',
        animation: 'fadeIn 0.4s ease-out'
      }}>
        <div>
          <h1 className="brand-title" style={{ textAlign: 'left', fontSize: '2rem' }}>EvacSense Suite</h1>
          <p className="brand-subtitle" style={{ textAlign: 'left' }}>Safety Administration Console</p>
        </div>
        
        <button 
          onClick={handleLogoutClick}
          className="btn btn-secondary" 
          style={{ width: 'auto', padding: '0.6rem 1.2rem', fontSize: '0.875rem' }}
        >
          Logout Secure Session
        </button>
      </div>

      {/* Admin Actions Alert Banner */}
      {(actionMessage || actionError || rejectingId) && (
        <div className="glass-panel" style={{ padding: '1.5rem', marginBottom: '2rem', animation: 'fadeIn 0.3s ease-out' }}>
          {actionMessage && (
            <div style={{
              background: 'rgba(16, 185, 129, 0.12)',
              border: '1px solid rgba(16, 185, 129, 0.25)',
              color: '#34d399',
              padding: '0.75rem 1rem',
              borderRadius: '10px',
              fontSize: '0.875rem'
            }}>{actionMessage}</div>
          )}
          {actionError && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.12)',
              border: '1px solid rgba(239, 68, 68, 0.25)',
              color: '#f87171',
              padding: '0.75rem 1rem',
              borderRadius: '10px',
              fontSize: '0.875rem'
            }}>{actionError}</div>
          )}
          {rejectingId && (
            <form onSubmit={handleRejectRequestSubmit} style={{ marginTop: '0.5rem', textAlign: 'left' }}>
              <h4 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1rem', marginBottom: '0.5rem' }}>Provide Verification Rejection Reason</h4>
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Employee ID does not match safety registry"
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  required
                  style={{ flex: 1 }}
                />
                <button type="submit" className="btn btn-primary" style={{ width: 'auto', padding: '0.6rem 1.2rem' }}>Confirm Rejection</button>
                <button type="button" className="btn btn-secondary" onClick={() => { setRejectingId(null); setRejectionReason(''); }} style={{ width: 'auto', padding: '0.6rem 1.2rem' }}>Cancel</button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* Main Grid Section */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: (user.role === 'System Admin' || user.role === 'Drill Coordinator') ? '1fr' : '1fr 2fr',
        gap: '2.5rem',
        alignItems: 'start'
      }}>
        
        {/* Left Column: User Profile Card (Standard users) */}
        {user.role !== 'System Admin' && user.role !== 'Drill Coordinator' && (
          <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.5s ease-out' }}>
            <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
              <div style={{
                width: '80px',
                height: '80px',
                borderRadius: '24px',
                background: 'linear-gradient(135deg, rgba(251, 191, 36, 0.1), rgba(153, 27, 27, 0.2))',
                border: '1px solid rgba(255,255,255,0.1)',
                margin: '0 auto 1.25rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '2rem',
                color: 'var(--accent-gold)'
              }}><Shield size={24} /></div>
              
              <h2 style={{ fontFamily: 'Outfit', fontSize: '1.5rem', fontWeight: 700, color: '#ffffff', marginBottom: '0.35rem' }}>{user.name}</h2>
              <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)', display: 'block', marginBottom: '0.75rem' }}>{user.email}</span>
              {renderBadge(user.role)}
            </div>

            <hr style={{ border: '0', borderTop: '1px solid var(--border-glass)', margin: '1.5rem 0' }} />

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', textAlign: 'left' }}>
              <div>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', display: 'block', fontWeight: 600 }}>Department</span>
                <span style={{ color: 'var(--text-primary)', fontSize: '0.9rem', fontWeight: 500 }}>{user.department || 'N/A'}</span>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', display: 'block', fontWeight: 600 }}>Authorization ID</span>
                <code style={{ color: 'var(--accent-gold)', fontSize: '0.85rem', fontFamily: 'monospace' }}>{user.id}</code>
              </div>
            </div>
          </div>
        )}

        {/* Right Column: Student/Teacher View */}
        {user.role !== 'System Admin' && user.role !== 'Drill Coordinator' && (
          <div className="glass-panel" style={{ padding: '2.5rem', animation: 'fadeIn 0.6s ease-out' }}>
            <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.25rem', marginBottom: '0.75rem' }}>Pre-Drill Presence Detection</h3>
            {activeDrill ? (
              <div style={{ background: 'rgba(16, 185, 129, 0.05)', border: '1px solid rgba(16, 185, 129, 0.15)', padding: '1.25rem', borderRadius: '12px', textAlign: 'left', animation: 'pulseGlow 2s infinite ease-in-out' }}>
                <strong style={{ color: '#34d399', fontSize: '1.1rem', marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}><AlertTriangle size={20} /> DRILL SESSION IS ACTIVE!</strong>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                  A drill coordinator has activated **{activeDrill.name}**. Students and teachers must execute pre-drill localization now!
                </p>
                <div style={{ background: 'rgba(255,255,255,0.03)', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border-glass)' }}>
                  <p style={{ color: '#ffffff', fontSize: '0.85rem' }}><strong>Please trigger localization inside your mobile application:</strong></p>
                  <ul style={{ paddingLeft: '1.25rem', color: 'var(--text-secondary)', fontSize: '0.8rem', marginTop: '0.5rem' }}>
                    <li>Wifi RSSI signals will auto-triangulate your classroom positioning.</li>
                    <li>If automatic scanning fails, use the Manual Location Selection dropdown.</li>
                  </ul>
                </div>
              </div>
            ) : (
              <div style={{ padding: '2rem', textAlign: 'center', background: 'rgba(255,255,255,0.01)', border: '1px dashed var(--border-glass)', borderRadius: '12px' }}>
                <span style={{ fontSize: '1.75rem', display: 'flex', marginBottom: '0.5rem', justifyContent: 'center' }}><Moon size={28} /></span>
                <strong style={{ color: '#ffffff', display: 'block' }}>No Active Drill Session</strong>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '0.25rem' }}>Drill baselines and classroom triangulation scans will open when a safety officer triggers the drill.</p>
              </div>
            )}
          </div>
        )}
        {/* Coordinators & Admin Live Dashboards Panel (Modules 1, 2, 4 & 5) */}
        {(user.role === 'System Admin' || user.role === 'Drill Coordinator') && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '2.5rem', width: '100%' }}>
            
            {/* Panel 1: Drill Control Panel (Module 2) */}
            <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.5s ease-out' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.4rem', fontWeight: 700 }}>{user.role === 'System Admin' ? 'Admin Dashboard' : 'Drill Coordination Dashboard'}</h3>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Trigger live drill events and evaluate classroom baseline presence tracking</p>
                </div>
                
                {user.role === 'Drill Coordinator' && (
                  <div style={{ display: 'flex', gap: '1rem' }}>
                    {!activeDrill ? (
                      <button 
                        onClick={handleStartDrill} 
                        className="btn btn-primary" 
                        style={{ width: 'auto', padding: '0.6rem 1.25rem', fontSize: '0.9rem' }}
                        disabled={loadingDrill}
                      >
                        <PlayCircle size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Initiate Earthquake Drill Run
                      </button>
                    ) : (
                      <button 
                        onClick={handleConcludeDrill} 
                        className="btn btn-primary" 
                        style={{ width: 'auto', padding: '0.6rem 1.25rem', fontSize: '0.9rem', background: 'linear-gradient(135deg, #ef4444, #b91c1c)', color: '#ffffff', boxShadow: 'none' }}
                        disabled={loadingDrill}
                      >
                        <StopCircle size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Conclude Active Drill Run
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* Status Ring Cockpit Metric Widgets */}
              {activeDrill && (
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                  gap: '1.5rem',
                  marginTop: '1.5rem'
                }}>
                  <div className="glass-panel" style={{ padding: '1.25rem', textAlign: 'left', borderLeft: '4px solid #3b82f6', background: 'rgba(255,255,255,0.01)', borderRadius: '12px' }}>
                    <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Total Registered</span>
                    <strong style={{ fontSize: '1.75rem', color: '#ffffff', display: 'block', marginTop: '0.25rem' }}>{totalParticipants}</strong>
                  </div>
                  <div className="glass-panel" style={{ padding: '1.25rem', textAlign: 'left', borderLeft: '4px solid #10b981', background: 'rgba(255,255,255,0.01)', borderRadius: '12px' }}>
                    <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Safely Arrived</span>
                    <strong style={{ fontSize: '1.75rem', color: '#10b981', display: 'block', marginTop: '0.25rem' }}>
                      {arrivedCount} <span style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>({totalParticipants > 0 ? Math.round((arrivedCount/totalParticipants)*100) : 0}%)</span>
                    </strong>
                  </div>
                  <div className="glass-panel" style={{ padding: '1.25rem', textAlign: 'left', borderLeft: '4px solid #fbbf24', background: 'rgba(255,255,255,0.01)', borderRadius: '12px' }}>
                    <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600 }}>Classroom Unverified</span>
                    <strong style={{ fontSize: '1.75rem', color: '#fbbf24', display: 'block', marginTop: '0.25rem' }}>{unverifiedCount}</strong>
                  </div>
                  <div className="glass-panel" style={{ 
                    padding: '1.25rem', 
                    textAlign: 'left', 
                    borderLeft: `4px solid ${distressCount > 0 ? '#ef4444' : '#6b7280'}`, 
                    background: distressCount > 0 ? 'rgba(239, 68, 68, 0.05)' : 'rgba(255,255,255,0.01)', 
                    borderRadius: '12px',
                    animation: distressCount > 0 ? 'pulseGlow 1.5s infinite ease-in-out' : 'none'
                  }}>
                    <span style={{ color: distressCount > 0 ? '#ef4444' : 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.25rem' }}><AlertCircle size={14} /> Distress Signals</span>
                    <strong style={{ fontSize: '1.75rem', color: distressCount > 0 ? '#ef4444' : '#ffffff', display: 'block', marginTop: '0.25rem' }}>{distressCount} Active</strong>
                  </div>
                </div>
              )}
            </div>

            {/* Emergency Flashing Distress Alert Panel */}
            {activeDrill && distressCount > 0 && (
              <div style={{
                background: 'rgba(239, 68, 68, 0.08)',
                border: '2px solid rgba(239, 68, 68, 0.35)',
                borderRadius: '16px',
                padding: '1.5rem',
                textAlign: 'left',
                animation: 'pulseGlow 1.5s infinite'
              }}>
                <h3 style={{ fontFamily: 'Outfit', color: '#f87171', fontSize: '1.25rem', fontWeight: 700, margin: '0 0 0.5rem 0', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <AlertCircle size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> EMERGENCY DISTRESS ALERTS ACTIVE ({distressCount})
                </h3>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
                  Immediate marshaling triage required at the locations listed below:
                </p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  {distressList.map((log) => (
                    <div key={log.userId} style={{
                      background: 'rgba(15,23,42,0.9)',
                      border: '1px solid rgba(239,68,68,0.2)',
                      borderRadius: '10px',
                      padding: '1.25rem',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center'
                    }}>
                      <div>
                        <strong style={{ color: '#ffffff', display: 'block', fontSize: '1.05rem' }}>{log.name} ({log.userId})</strong>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', display: 'block', marginTop: '0.15rem' }}>
                          Role: {log.role} | Department: {log.department}
                        </span>
                        <span style={{ color: '#f87171', fontSize: '0.88rem', display: 'block', marginTop: '0.4rem', fontWeight: 600 }}>
                          <MapPin size={16} style={{marginRight: '6px', verticalAlign: 'middle'}} /> Last Known Coordinates: {log.location}
                        </span>
                      </div>
                      <button
                        onClick={() => handleMarshalClear(log.userId)}
                        className="btn btn-primary"
                        style={{
                          width: 'auto',
                          padding: '0.5rem 1rem',
                          fontSize: '0.8rem',
                          background: 'linear-gradient(135deg, #10b981, #059669)',
                          color: '#ffffff',
                          border: 'none',
                          boxShadow: 'none'
                        }}
                      >
                        Mark Safe & Clear Alert
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Tabs Navigation */}
            <div style={{
              display: 'flex',
              gap: '1rem',
              borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
              paddingBottom: '1.25rem',
              marginBottom: '0.5rem'
            }}>
              <button
                onClick={() => setActiveTab('occupancy')}
                className={`btn ${activeTab === 'occupancy' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ width: 'auto', padding: '0.6rem 1.5rem', fontSize: '0.85rem' }}
              >
                <BarChart2 size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Drill Attendance & Occupancy
              </button>
              {user.role === 'Drill Coordinator' && (
                <>
                  <button
                    onClick={() => {
                      setActiveTab('navigation');
                      fetchGraph();
                    }}
                    className={`btn ${activeTab === 'navigation' ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ width: 'auto', padding: '0.6rem 1.5rem', fontSize: '0.85rem' }}
                  >
                    <MapIcon size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Evacuation Path Designer & Rerouting
                  </button>
                  <button
                    onClick={() => {
                      setActiveTab('reports');
                      fetchDrillsList();
                    }}
                    className={`btn ${activeTab === 'reports' ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ width: 'auto', padding: '0.6rem 1.5rem', fontSize: '0.85rem' }}
                  >
                    <ClipboardList size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Compliance Safety Reports
                  </button>
                </>
              )}
            </div>

            {activeTab === 'occupancy' && (
              <>
                {/* Floor-by-Floor Clearance Checklists (Module 4 clearances) */}
                {activeDrill && floorClearances.length > 0 && (
                  <div className="glass-panel" style={{ padding: '2rem', textAlign: 'left' }}>
                    <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.25rem', fontWeight: 700, marginBottom: '1.25rem' }}>
                      Floor-by-Floor Clearance Checklist
                    </h3>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem' }}>
                      {floorClearances.map(fc => {
                        const isCleared = fc.remaining === 0 && fc.totalOccupants > 0;
                        return (
                          <div key={fc.floor} style={{
                            background: 'rgba(255,255,255,0.01)',
                            border: `1px solid ${isCleared ? 'rgba(16, 185, 129, 0.25)' : 'rgba(255,255,255,0.05)'}`,
                            borderRadius: '12px',
                            padding: '1.25rem',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '0.5rem'
                          }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                              <strong style={{ color: '#ffffff', fontSize: '0.95rem' }}>Floor {fc.floor}</strong>
                              <span style={{
                                fontSize: '0.7rem',
                                fontWeight: 700,
                                color: isCleared ? '#34d399' : '#f59e0b',
                                background: isCleared ? 'rgba(16,185,129,0.1)' : 'rgba(245,158,11,0.1)',
                                padding: '0.15rem 0.4rem',
                                borderRadius: '4px'
                              }}>
                                {isCleared ? 'CLEARED' : 'IN PROGRESS'}
                              </span>
                            </div>
                            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                              Arrived: **{fc.evacuated}** of **{fc.totalOccupants}** baseline
                            </span>
                            <div style={{
                              width: '100%',
                              height: '6px',
                              background: 'rgba(255,255,255,0.05)',
                              borderRadius: '3px',
                              overflow: 'hidden',
                              marginTop: '0.25rem'
                            }}>
                              <div style={{
                                width: `${fc.totalOccupants > 0 ? (fc.evacuated / fc.totalOccupants) * 100 : 0}%`,
                                height: '100%',
                                background: isCleared ? '#10b981' : '#f59e0b',
                                transition: 'width 0.4s ease'
                              }}></div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Panel 2: Live Room Headcounts & Triangulations Dashboard (Module 2) */}
                {activeDrill && (
                  <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.6s ease-out' }}>
                    <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.35rem', fontWeight: 700, marginBottom: '1.25rem' }}>
                      Campus Room Occupancies Dashboard
                    </h3>

                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead>
                          <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Room Code</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Room Name</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Floor</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Active Occupancy</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Auto (RSSI)</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Manual Fallbacks</th>
                          </tr>
                        </thead>
                        <tbody>
                          {roomsOccupancy.map((room) => (
                            <tr key={room.roomId} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem' }}>
                                <code style={{ color: 'var(--accent-gold)' }}>{room.roomId}</code>
                              </td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.9rem', fontWeight: 600, color: '#ffffff' }}>
                                {room.roomName}
                              </td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                                Floor {room.floor}
                              </td>
                              <td style={{ padding: '0.75rem 1rem' }}>
                                <span style={{ 
                                  fontSize: '0.9rem', 
                                  fontWeight: 700, 
                                  color: room.totalHeadcount > 0 ? '#34d399' : 'var(--text-muted)'
                                }}>
                                  {room.totalHeadcount} Students
                                </span>
                              </td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: '#60a5fa' }}>{room.autoRSSI}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: '#fbbf24' }}>{room.manualOverride}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* Unaccounted Students Safety Roster (Module 4 missing triage) */}
                {activeDrill && missingList.length > 0 && (
                  <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.6s ease-out' }}>
                    <div style={{ textAlign: 'left', marginBottom: '1.5rem' }}>
                      <h3 style={{ fontFamily: 'Outfit', color: '#ef4444', fontSize: '1.35rem', fontWeight: 700 }}>
                        Unaccounted Students Safety Roster ({missingList.length})
                      </h3>
                      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                        These students are registered at classroom baselines but have not checked in at the assembly area safe zone.
                      </p>
                    </div>

                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead>
                          <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>ID Number</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Full Name</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Department</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Classroom Origin</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Current Status</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Safety Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {missingList.map((stu) => (
                            <tr key={stu.userId} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem' }}>
                                <code style={{ color: 'var(--accent-gold)' }}>{stu.userId}</code>
                              </td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.9rem', color: '#ffffff', fontWeight: 600 }}>{stu.name}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{stu.department}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: '#60a5fa' }}>{stu.originRoom}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: stu.status === 'Pending Marshal Clearance' ? '#fbbf24' : (stu.status === 'Verification Failed' ? '#ef4444' : 'var(--text-secondary)') }}>
                                {stu.status || 'Absent'}
                              </td>
                              <td style={{ padding: '0.75rem 1rem' }}>
                                {stu.status === 'Pending Marshal Clearance' && (
                                  <button
                                    onClick={() => handleMarshalClear(stu.userId)}
                                    className="btn btn-primary"
                                    style={{
                                      width: 'auto',
                                      padding: '0.35rem 0.75rem',
                                      fontSize: '0.75rem',
                                      background: 'linear-gradient(135deg, #10b981, #059669)',
                                      boxShadow: 'none'
                                    }}
                                  >
                                    Marshal Clear
                                  </button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* Panel 3: Location-Unverified Student Triage Roster (Module 2 unverified scans) */}
                {activeDrill && unverifiedList.length > 0 && (
                  <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.6s ease-out' }}>
                    <h3 style={{ fontFamily: 'Outfit', color: '#fbbf24', fontSize: '1.35rem', fontWeight: 700, marginBottom: '0.25rem' }}>
                      Location-Unverified Student Triage Roster
                    </h3>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>
                      These students have not registered classroom locations. Keep track during manual assembly checks.
                    </p>

                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead>
                          <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Student ID</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Full Name</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Institutional Email</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Department</th>
                          </tr>
                        </thead>
                        <tbody>
                          {unverifiedList.map((stu) => (
                            <tr key={stu.userId} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem' }}>
                                <code>{stu.userId}</code>
                              </td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.9rem', color: '#ffffff', fontWeight: 600 }}>{stu.name}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{stu.email}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>{stu.department}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* Panel 4: Administrative Pending Staff Accounts (Module 1 approvals) */}
                {user.role === 'System Admin' && (
                  <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.5s ease-out' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                      <div>
                        <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.35rem', fontWeight: 700 }}>
                          Pending Staff Registration Requests
                        </h3>
                        <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                          Review, approve, or reject Teacher/Coordinator registration payloads
                        </p>
                      </div>
                      <button 
                        onClick={fetchPendingRequests} 
                        className="btn btn-secondary" 
                        style={{ width: 'auto', padding: '0.4rem 0.8rem', fontSize: '0.75rem' }}
                        disabled={loadingRequests}
                      >
                        Sync Requests
                      </button>
                    </div>

                    {pendingRequests.length === 0 ? (
                      <div style={{
                        padding: '4rem 2rem',
                        textAlign: 'center',
                        background: 'rgba(255,255,255,0.02)',
                        borderRadius: '12px',
                        border: '1px dashed var(--border-glass)'
                      }}>
                        <CheckCircle size={48} color="#34d399" style={{ marginBottom: '1rem', opacity: 0.8 }} />
                        <h4 style={{ color: '#ffffff', fontSize: '1.25rem', fontWeight: 600, margin: 0 }}>All Caught Up!</h4>
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.5rem' }}>There are no pending staff registration requests to review at this time.</p>
                      </div>
                    ) : (
                      <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                          <thead>
                            <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                              <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Employee ID</th>
                              <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Full Name</th>
                              <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Institutional Email</th>
                              <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Role</th>
                              <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Review Actions</th>
                            </tr>
                          </thead>
                          <tbody>
                            {pendingRequests.map((req) => (
                              <tr key={req.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                                <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem' }}>
                                  <code style={{ color: 'var(--accent-gold)' }}>{req.id}</code>
                                </td>
                                <td style={{ padding: '0.75rem 1rem', fontSize: '0.9rem', fontWeight: 600, color: '#ffffff' }}>{req.name}</td>
                                <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{req.email}</td>
                                <td style={{ padding: '0.75rem 1rem' }}>{renderBadge(req.role)}</td>
                                <td style={{ padding: '0.75rem 1rem', display: 'flex', gap: '0.5rem' }}>
                                  <button onClick={() => handleApproveRequest(req.id)} className="btn btn-primary" style={{ width: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.75rem', background: 'linear-gradient(135deg, #10b981, #059669)', color: '#ffffff', boxShadow: 'none' }}>Approve</button>
                                  <button onClick={() => setRejectingId(req.id)} className="btn btn-secondary" style={{ width: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.75rem', border: '1px solid #ef4444', color: '#ef4444' }}>Reject</button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                )}

                {/* Panel 5: Admin User Directory List (Module 1 management) */}
                {user.role === 'System Admin' && (
                  <div className="glass-panel" style={{ padding: '2.25rem', animation: 'fadeIn 0.6s ease-out' }}>
                    <div style={{ display: 'flex', justify: 'space-between', align: 'center', marginBottom: '1.5rem' }}>
                      <div>
                        <h3 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.35rem', fontWeight: 700 }}>System User Directory</h3>
                        <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Active registered user accounts database</p>
                      </div>
                      <button 
                        onClick={fetchUsers} 
                        className="btn btn-secondary" 
                        style={{ width: 'auto', padding: '0.4rem 0.8rem', fontSize: '0.75rem' }}
                        disabled={loadingUsers}
                      >
                        Sync Directory
                      </button>
                    </div>

                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead>
                          <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>UID</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Identity Name</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Email</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Role</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Security Status</th>
                            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Policy Override Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {usersList.map((usr) => (
                            <tr key={usr.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)', transition: 'background 0.2s' }}>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem' }}><code style={{ color: 'var(--accent-gold)' }}>{usr.id}</code></td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.9rem', fontWeight: 600, color: '#ffffff' }}>{usr.name}</td>
                              <td style={{ padding: '0.75rem 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{usr.email}</td>
                              <td style={{ padding: '0.75rem 1rem' }}>{renderBadge(usr.role)}</td>
                              <td style={{ padding: '0.75rem 1rem' }}>
                                <span style={{ 
                                  fontSize: '0.75rem', 
                                  fontWeight: 700, 
                                  color: usr.status === 'locked' ? '#ef4444' : usr.status === 'Rejected' ? '#ef4444' : '#10b981',
                                  background: usr.status === 'locked' ? 'rgba(239, 68, 68, 0.1)' : usr.status === 'Rejected' ? 'rgba(239, 68, 68, 0.1)' : 'rgba(16, 185, 129, 0.1)',
                                  padding: '0.2rem 0.5rem',
                                  borderRadius: '6px'
                                }}>{usr.status === 'locked' ? 'LOCKED' : usr.status === 'Rejected' ? 'REJECTED' : 'ACTIVE'}</span>
                              </td>
                              <td style={{ padding: '0.75rem 1rem' }}>
                                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                                  <select
                                    className="form-input"
                                    value={usr.role}
                                    onChange={(e) => handleRoleChange(usr.id, e.target.value)}
                                    style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem', background: 'rgba(15,23,42,0.85)', borderColor: 'rgba(255,255,255,0.1)', borderRadius: '8px', cursor: 'pointer', color: '#ffffff' }}
                                  >
                                    <option value="Student">Student</option>
                                    <option value="Teacher">Teacher/Staff</option>
                                    <option value="Drill Coordinator">Drill Coordinator</option>
                                    <option value="System Admin">System Admin</option>
                                  </select>
                                  {usr.status === 'locked' && (
                                    <button
                                      onClick={() => handleUnlockAccount(usr.id)}
                                      className="btn btn-primary"
                                      style={{
                                        width: 'auto',
                                        padding: '0.3rem 0.7rem',
                                        fontSize: '0.7rem',
                                        background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                                        color: '#ffffff',
                                        border: 'none',
                                        boxShadow: 'none',
                                        whiteSpace: 'nowrap',
                                        fontWeight: 700
                                      }}
                                    >
                                      🔓 Unlock
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </>
            )}

            {activeTab === 'navigation' && (
              /* Evacuation Path Designer Tab Component */
              <div className="glass-panel" style={{ padding: '2.5rem', animation: 'fadeIn 0.5s ease-out' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '2.5rem' }}>
                  {/* Visual Map Canvas */}
                  <div>
                    <h4 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.25rem', marginBottom: '0.25rem', textAlign: 'left' }}>
                      <Building size={20} style={{marginRight: '8px', verticalAlign: 'middle'}} /> NGE Building — Evacuation Route Map
                    </h4>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginBottom: '1rem', textAlign: 'left' }}>
                      Dr. Nicolas G. Escario Sr. Building • CIT-U Campus (B1)
                    </p>

                    {/* Floor Selector Tabs */}
                    <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
                      {[1, 2, 3, 4].map(f => (
                        <button
                          key={f}
                          onClick={() => setSelectedFloor(f)}
                          className={`btn ${selectedFloor === f ? 'btn-primary' : 'btn-secondary'}`}
                          style={{ width: 'auto', padding: '0.45rem 1rem', fontSize: '0.78rem', borderRadius: '8px', transition: 'all 0.3s' }}
                        >
                          {f === 1 ? '1F Ground' : f === 2 ? '2F Labs' : f === 3 ? '3F Nursing' : '4F Medical'}
                        </button>
                      ))}
                    </div>

                    <p style={{ color: 'var(--text-secondary)', fontSize: '0.8rem', marginBottom: '1.25rem', textAlign: 'left' }}>
                      <strong style={{ color: 'rgba(255,255,255,0.6)' }}>{NGE_FLOOR_TITLES[selectedFloor]}</strong> — 
                      <strong style={{ color: '#fbbf24' }}> Click a node to test evacuation routing.</strong>
                      <strong style={{ color: '#ef4444' }}> Click a connection to toggle blockage.</strong>
                    </p>

                    {/* SVG Canvas Map */}
                    <div style={{
                      background: 'rgba(15, 23, 42, 0.7)',
                      borderRadius: '16px',
                      border: '1px solid rgba(255,255,255,0.1)',
                      padding: '1.5rem',
                      display: 'flex',
                      justifyContent: 'center',
                      position: 'relative',
                      boxShadow: 'inset 0 0 20px rgba(0,0,0,0.6)'
                    }}>
                      <svg width="660" height="420" style={{ overflow: 'visible' }}>
                        {/* Floor title watermark */}
                        <text x="330" y="415" textAnchor="middle" fill="rgba(255,255,255,0.08)" style={{ fontSize: '0.65rem', fontFamily: 'monospace', letterSpacing: '0.1em' }}>
                          NGE BUILDING • FLOOR {selectedFloor}
                        </text>
                        {/* 1. Connections (Edges) */}
                        {edges.filter(edge => {
                          const fc = NGE_FLOOR_LAYOUTS[selectedFloor] || {};
                          return fc[edge.fromNodeId] && fc[edge.toNodeId];
                        }).map((edge, index) => {
                          const floorCoords = NGE_FLOOR_LAYOUTS[selectedFloor] || {};
                          const fromCoords = floorCoords[edge.fromNodeId];
                          const toCoords = floorCoords[edge.toNodeId];
                          
                          const isPathEdge = testPath.some((node, i) => {
                            if (i === 0) return false;
                            const prev = testPath[i - 1];
                            return (prev.id === edge.fromNodeId && node.id === edge.toNodeId) ||
                                   (prev.id === edge.toNodeId && node.id === edge.fromNodeId);
                          });

                          return (
                            <g key={edge.id}>
                              <line
                                x1={fromCoords.x}
                                y1={fromCoords.y}
                                x2={toCoords.x}
                                y2={toCoords.y}
                                stroke={edge.isBlocked ? '#ef4444' : isPathEdge ? '#10b981' : 'rgba(255,255,255,0.15)'}
                                strokeWidth={edge.isBlocked ? 4 : isPathEdge ? 6 : 3}
                                strokeDasharray={edge.isBlocked ? '6,6' : 'none'}
                                onClick={() => handleToggleBlockage(edge.id, edge.isBlocked)}
                                style={{ transition: 'all 0.3s', cursor: 'pointer' }}
                              />
                              {isPathEdge && (
                                <line
                                  x1={fromCoords.x}
                                  y1={fromCoords.y}
                                  x2={toCoords.x}
                                  y2={toCoords.y}
                                  stroke="#34d399"
                                  strokeWidth={10}
                                  opacity={0.3}
                                  style={{ filter: 'blur(4px)', pointerEvents: 'none' }}
                                />
                              )}
                            </g>
                          );
                        })}

                        {/* 2. Nodes */}
                        {nodes.filter(node => (NGE_FLOOR_LAYOUTS[selectedFloor] || {})[node.id]).map((node, index) => {
                          const coords = (NGE_FLOOR_LAYOUTS[selectedFloor] || {})[node.id];
                          const isSelected = selectedOrigin === node.id;
                          const isPartOfPath = testPath.some(n => n.id === node.id);

                          return (
                            <g 
                              key={node.id} 
                              transform={`translate(${coords.x}, ${coords.y})`}
                              onClick={() => {
                                setSelectedOrigin(node.id);
                                calculateTestRoute(node.id);
                              }}
                              style={{ cursor: 'pointer' }}
                            >
                              {isSelected && (
                                <circle r="22" fill="none" stroke="var(--accent-gold)" strokeWidth="2" style={{ animation: 'pulseGlow 1.5s infinite' }} />
                              )}
                              
                              <circle 
                                r="14" 
                                fill={node.id.startsWith('EXIT') ? '#047857' : isSelected ? 'var(--accent-gold)' : isPartOfPath ? '#10b981' : coords.color}
                                stroke="#0f172a" 
                                strokeWidth="3"
                                style={{ transition: 'all 0.3s' }}
                              />
                              
                              <text
                                y="-22"
                                textAnchor="middle"
                                fill="#ffffff"
                                style={{ fontSize: '0.75rem', fontFamily: 'Outfit', fontWeight: 600, pointerEvents: 'none' }}
                              >
                                {coords.label}
                              </text>

                              <text
                                y="4"
                                textAnchor="middle"
                                fill={isSelected ? '#020617' : '#ffffff'}
                                style={{ fontSize: '0.65rem', fontFamily: 'monospace', fontWeight: 700, pointerEvents: 'none' }}
                              >
                                {node.id.split('-').slice(1).join('-')}
                              </text>
                            </g>
                          );
                        })}
                      </svg>
                    </div>

                    {/* Test Path Results and Instructions */}
                    <div style={{ marginTop: '2rem', textAlign: 'left' }}>
                      <h4 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.2rem', marginBottom: '0.75rem', marginTop: '2rem' }}>
                        {selectedOrigin ? `${NGE_FLOOR_LAYOUTS[selectedFloor]?.[selectedOrigin]?.label || selectedOrigin} Pathfinding Simulation Output` : 'Pathfinding Simulation Output'}
                      </h4>
                      {selectedOrigin ? (
                        <div style={{
                          padding: '1.25rem',
                          background: 'rgba(255,255,255,0.02)',
                          border: '1px solid rgba(255,255,255,0.06)',
                          borderRadius: '12px'
                        }}>
                          <div style={{ marginBottom: '1rem' }}>
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', display: 'block', marginBottom: '0.25rem' }}>Simulation Origin Node</span>
                            <strong style={{ color: '#ffffff', fontSize: '1.1rem' }}>{NGE_FLOOR_LAYOUTS[selectedFloor]?.[selectedOrigin]?.label || selectedOrigin}</strong>
                          </div>

                          {errorPath ? (
                            <div style={{ color: '#ef4444', fontWeight: 600, fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}><AlertTriangle size={16} /> {errorPath}</div>
                          ) : (
                            <div>
                              <p style={{ color: '#34d399', fontWeight: 600, fontSize: '0.95rem', marginBottom: '0.75rem' }}>
                                <CheckCircle2 size={16} style={{marginRight: '6px', verticalAlign: 'middle'}} /> Shortest evacuation route successfully computed.
                              </p>
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                                {testInstructions.map((inst, index) => (
                                  <div key={index} style={{ display: 'flex', gap: '0.75rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                                    <span style={{ color: 'var(--accent-gold)', fontWeight: 700 }}>Step {index + 1}:</span>
                                    <span>{inst.text}</span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      ) : (
                        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', fontStyle: 'italic' }}>
                          Select any room node on the blueprint canvas to test shortest-path evacuation navigation step-by-step.
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Edge Connection List and Controls Panel */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', textAlign: 'left' }}>
                    
                    {/* List of Pathway Links */}
                    <div style={{
                      background: 'rgba(255, 255, 255, 0.02)',
                      border: '1px solid rgba(255, 255, 255, 0.05)',
                      borderRadius: '16px',
                      padding: '1.5rem',
                      maxHeight: '260px',
                      overflowY: 'auto'
                    }}>
                      <h5 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.1rem', marginBottom: '0.75rem' }}>
                        Active Map Connections
                      </h5>
                      
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                        {edges.map((edge) => (
                          <div 
                            key={edge.id}
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              padding: '0.5rem 0.75rem',
                              background: edge.isBlocked ? 'rgba(239, 68, 68, 0.06)' : 'rgba(255,255,255,0.01)',
                              border: `1px solid ${edge.isBlocked ? 'rgba(239, 68, 68, 0.2)' : 'rgba(255,255,255,0.05)'}`,
                              borderRadius: '8px'
                            }}
                          >
                            <div style={{ fontSize: '0.8rem', color: '#ffffff', fontWeight: 600 }}>
                              <code>{edge.fromNodeId.split('-')[1] || edge.fromNodeId}</code>
                              <span style={{ color: 'var(--text-muted)', margin: '0 0.4rem' }}>⇄</span>
                              <code>{edge.toNodeId.split('-')[1] || edge.toNodeId}</code>
                            </div>
                            
                            <button
                              onClick={() => handleToggleBlockage(edge.id, edge.isBlocked)}
                              className="btn btn-secondary"
                              style={{
                                width: 'auto',
                                padding: '0.25rem 0.6rem',
                                fontSize: '0.7rem',
                                border: edge.isBlocked ? '1px solid #10b981' : '1px solid #ef4444',
                                color: edge.isBlocked ? '#10b981' : '#ef4444',
                                borderRadius: '6px'
                              }}
                            >
                              {edge.isBlocked ? 'Clear' : 'Block'}
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Add Map Pathway Link Form */}
                    <form onSubmit={handleAddEdge} style={{
                      background: 'rgba(255, 255, 255, 0.02)',
                      border: '1px solid rgba(255, 255, 255, 0.05)',
                      borderRadius: '16px',
                      padding: '1.5rem'
                    }}>
                      <h5 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.1rem', marginBottom: '1rem' }}>
                        Add Route Connection
                      </h5>
                      
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                        <div>
                          <label style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Origin Node</label>
                          <select
                            value={newEdgeFrom}
                            onChange={(e) => setNewEdgeFrom(e.target.value)}
                            className="form-input"
                            style={{ padding: '0.5rem', fontSize: '0.8rem', background: '#020617', color: '#ffffff', marginTop: '0.25rem' }}
                            required
                          >
                            <option value="">-- Select node --</option>
                            {nodes.map(n => <option key={n.id} value={n.id}>{n.id} ({n.name.split(' ')[0]})</option>)}
                          </select>
                        </div>

                        <div>
                          <label style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Destination Node</label>
                          <select
                            value={newEdgeTo}
                            onChange={(e) => setNewEdgeTo(e.target.value)}
                            className="form-input"
                            style={{ padding: '0.5rem', fontSize: '0.8rem', background: '#020617', color: '#ffffff', marginTop: '0.25rem' }}
                            required
                          >
                            <option value="">-- Select node --</option>
                            {nodes.map(n => <option key={n.id} value={n.id}>{n.id} ({n.name.split(' ')[0]})</option>)}
                          </select>
                        </div>

                        <div>
                          <label style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Distance Weight (m)</label>
                          <input
                            type="number"
                            step="0.1"
                            value={newEdgeWeight}
                            onChange={(e) => setNewEdgeWeight(e.target.value)}
                            className="form-input"
                            style={{ padding: '0.5rem', fontSize: '0.8rem', background: '#020617', color: '#ffffff', marginTop: '0.25rem' }}
                            required
                          />
                        </div>

                        <button type="submit" className="btn btn-primary" style={{ padding: '0.5rem', fontSize: '0.8rem', marginTop: '0.5rem' }}>
                          <Link size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Bind Connection Link
                        </button>
                      </div>
                    </form>
                    
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'reports' && (
              /* Historical Drill Safety Compliance Reports Hub component */
              <div className="glass-panel" style={{ padding: '2.5rem', animation: 'fadeIn 0.5s ease-out', textAlign: 'left' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '2.5rem' }}>
                  
                  {/* Left History List Panel */}
                  <div>
                    <h4 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.25rem', marginBottom: '0.5rem' }}>
                      Historical Safety Reviews
                    </h4>
                    <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginBottom: '1.5rem' }}>
                      Select a completed earthquake drill session to inspect analytics safety logs.
                    </p>
                    
                    <button
                      onClick={fetchDrillsList}
                      className="btn btn-secondary"
                      style={{ width: '100%', fontSize: '0.8rem', marginBottom: '1.5rem', border: '1px dashed rgba(255,255,255,0.2)' }}
                    >
                      <RefreshCw size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Sync Completed Drills
                    </button>
                    
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxHeight: '350px', overflowY: 'auto' }}>
                      {drillsList.map((dr) => (
                        <button
                          key={dr.id}
                          onClick={() => {
                            setSelectedReportId(dr.id);
                            loadReportDetails(dr.id);
                          }}
                          className={`btn ${selectedReportId === dr.id ? 'btn-primary' : 'btn-secondary'}`}
                          style={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'flex-start',
                            padding: '0.85rem 1.25rem',
                            borderRadius: '10px',
                            textAlign: 'left',
                            transition: 'all 0.2s'
                          }}
                        >
                          <strong style={{ color: '#ffffff', fontSize: '0.9rem' }}>{dr.name}</strong>
                          <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                            Status: <span style={{ color: dr.status === 'concluded' ? '#10b981' : '#f59e0b', fontWeight: 700 }}>{dr.status.toUpperCase()}</span> | Code: DR-{dr.id}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Right Compliance Review Document Details */}
                  <div>
                    {loadingReport && (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', justifyContent: 'center', alignItems: 'center', height: '300px' }}>
                        <div style={{ width: '40px', height: '40px', borderRadius: '50%', border: '3px solid rgba(255,255,255,0.05)', borderTopColor: 'var(--accent-gold)', animation: 'pulseGlow 1s infinite linear' }} />
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Compiling safety parameters...</span>
                      </div>
                    )}

                    {!loadingReport && reportData && (
                      <div id="safety-compliance-document" style={{
                        background: 'rgba(15, 23, 42, 0.45)',
                        border: '1px solid rgba(255,255,255,0.08)',
                        borderRadius: '16px',
                        padding: '2rem',
                        boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
                        animation: 'fadeIn 0.4s ease-out'
                      }}>
                        {/* Report Header */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '1.25rem', marginBottom: '1.5rem' }}>
                          <div>
                            <h4 style={{ fontFamily: 'Outfit', color: '#ffffff', fontSize: '1.35rem', fontWeight: 700, margin: 0 }}>
                              {reportData.name}
                            </h4>
                            <span style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>
                              Institutional Earthquake Safety Clearance • Report ID: #{reportData.drillId}
                            </span>
                          </div>
                          <div style={{ textAlign: 'right' }}>
                            <span style={{
                              fontSize: '0.82rem',
                              fontWeight: 800,
                              color: reportData.complianceAssessment.drillGrade === 'EXCELLENT' ? '#10b981' : reportData.complianceAssessment.drillGrade === 'SATISFACTORY' ? '#60a5fa' : '#f87171',
                              background: reportData.complianceAssessment.drillGrade === 'EXCELLENT' ? 'rgba(16,185,129,0.1)' : 'rgba(96,165,250,0.1)',
                              padding: '0.35rem 0.85rem',
                              borderRadius: '8px',
                              border: `1px solid ${reportData.complianceAssessment.drillGrade === 'EXCELLENT' ? 'rgba(16,185,129,0.2)' : 'rgba(96,165,250,0.2)'}`
                            }}>{reportData.complianceAssessment.drillGrade} GRADE</span>
                          </div>
                        </div>

                        {/* Summary Metrics */}
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '1.25rem', marginBottom: '1.5rem' }}>
                          <div style={{ background: 'rgba(255,255,255,0.01)', padding: '1rem', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', display: 'block', textTransform: 'uppercase', fontWeight: 600 }}>Total Registered</span>
                            <strong style={{ fontSize: '1.35rem', color: '#ffffff', marginTop: '0.2rem', display: 'block' }}>{reportData.totalParticipants}</strong>
                          </div>
                          <div style={{ background: 'rgba(255,255,255,0.01)', padding: '1rem', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', display: 'block', textTransform: 'uppercase', fontWeight: 600 }}>Evacuation Rate</span>
                            <strong style={{ fontSize: '1.35rem', color: '#10b981', marginTop: '0.2rem', display: 'block' }}>{reportData.evacuationRate}%</strong>
                          </div>
                          <div style={{ background: 'rgba(255,255,255,0.01)', padding: '1rem', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', display: 'block', textTransform: 'uppercase', fontWeight: 600 }}>Total Clear Time</span>
                            <strong style={{ fontSize: '1.35rem', color: '#60a5fa', marginTop: '0.2rem', display: 'block' }}>{reportData.clearTime}</strong>
                          </div>
                          <div style={{ background: 'rgba(255,255,255,0.01)', padding: '1rem', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.05)' }}>
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem', display: 'block', textTransform: 'uppercase', fontWeight: 600 }}>Unaccounted</span>
                            <strong style={{ fontSize: '1.35rem', color: reportData.missingCount > 0 ? '#ef4444' : '#10b981', marginTop: '0.2rem', display: 'block' }}>{reportData.missingCount}</strong>
                          </div>
                        </div>

                        {/* Floor clearance times */}
                        <div style={{ marginBottom: '1.5rem' }}>
                          <h5 style={{ color: '#ffffff', fontSize: '0.98rem', marginBottom: '0.6rem', fontFamily: 'Outfit' }}>Floor-by-Floor Clearance Analytics</h5>
                          <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                              <thead>
                                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', textAlign: 'left' }}>
                                  <th style={{ padding: '0.6rem 0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Floor level</th>
                                  <th style={{ padding: '0.6rem 0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Baseline Roster</th>
                                  <th style={{ padding: '0.6rem 0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Evacuated Safely</th>
                                  <th style={{ padding: '0.6rem 0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Remaining</th>
                                  <th style={{ padding: '0.6rem 0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>Egress Duration</th>
                                </tr>
                              </thead>
                              <tbody>
                                {reportData.floorClearance.map(fc => (
                                  <tr key={fc.floor} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                                    <td style={{ padding: '0.6rem 0.5rem', color: '#ffffff', fontWeight: 600 }}>Floor {fc.floor}</td>
                                    <td style={{ padding: '0.6rem 0.5rem' }}>{fc.totalOccupants} occupants</td>
                                    <td style={{ padding: '0.6rem 0.5rem', color: '#10b981', fontWeight: 600 }}>{fc.evacuated}</td>
                                    <td style={{ padding: '0.6rem 0.5rem', color: fc.remaining > 0 ? '#ef4444' : 'var(--text-muted)' }}>{fc.remaining}</td>
                                    <td style={{ padding: '0.6rem 0.5rem', color: '#60a5fa', fontWeight: 600 }}>{fc.clearTime}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>

                        {/* Recommendations */}
                        <div style={{
                          background: 'rgba(251, 191, 36, 0.02)',
                          padding: '1.25rem',
                          borderRadius: '12px',
                          border: '1px solid rgba(251, 191, 36, 0.12)',
                          marginBottom: '1.5rem'
                        }}>
                          <h5 style={{ color: 'var(--accent-gold)', fontSize: '0.92rem', marginBottom: '0.5rem', fontWeight: 700, fontFamily: 'Outfit' }}>
                            Compliance Recommendations List
                          </h5>
                          <ul style={{ margin: 0, paddingLeft: '1.25rem', color: 'var(--text-secondary)', fontSize: '0.82rem', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                            {reportData.complianceAssessment.recommendations.map((rec, i) => (
                              <li key={i}>{rec}</li>
                            ))}
                          </ul>
                        </div>

                        {/* Export Buttons */}
                        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: '1.25rem' }}>
                          <a
                            href={`${API_BASE_URL}/api/reports/export/csv/${reportData.drillId}`}
                            className="btn btn-secondary"
                            style={{ width: 'auto', padding: '0.6rem 1.25rem', fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '0.35rem' }}
                          >
                            <Download size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Download Data Matrix (CSV)
                          </a>
                          <button
                            onClick={() => window.print()}
                            className="btn btn-primary"
                            style={{ width: 'auto', padding: '0.6rem 1.25rem', fontSize: '0.82rem' }}
                          >
                            <Printer size={18} style={{marginRight: '8px', verticalAlign: 'middle'}} /> Print Safety Certificate (PDF)
                          </button>
                        </div>
                      </div>
                    )}

                    {!reportData && !loadingReport && (
                      <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        height: '350px',
                        border: '1px dashed var(--border-glass)',
                        borderRadius: '16px',
                        background: 'rgba(255,255,255,0.01)',
                        color: 'var(--text-muted)'
                      }}>
                        <span style={{ fontSize: '2.5rem', marginBottom: '0.75rem', display: 'flex', justifyContent: 'center' }}><ClipboardList size={40} /></span>
                        <strong style={{ color: '#ffffff' }}>No Compliance Report Selected</strong>
                        <p style={{ fontSize: '0.82rem', marginTop: '0.25rem' }}>Select a finished drill session on the left to compile safety recommendations and clear certificates.</p>
                      </div>
                    )}
                  </div>

                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
