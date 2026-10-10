import React, { useState, useEffect, useRef } from 'react';

type Telemetry = {
  clientId: string;
  pose: string;
  timestamp: number;
};

export default function App() {
  const [telemetry, setTelemetry] = useState<Record<string, Telemetry>>({});
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    const wsUrl = 'ws://localhost:8080/ws/telemetry';
    
    const connect = () => {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => setConnected(true);
      ws.onclose = () => {
        setConnected(false);
        setTimeout(connect, 3000); // Auto reconnect
      };
      ws.onmessage = (event) => {
        try {
          const data: Telemetry = JSON.parse(event.data);
          if (data.clientId) {
            setTelemetry((prev) => ({
              ...prev,
              [data.clientId]: data,
            }));
          }
        } catch (e) {
          console.error('Invalid telemetry JSON', e);
        }
      };
    };

    connect();

    return () => {
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.close();
      }
    };
  }, []);

  const sendCommand = async (command: string, useQuantization: boolean) => {
    try {
      await fetch('/api/mqtt/federated/command', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ command, use_quantization: useQuantization })
      });
    } catch (err) {
      console.error(err);
    }
  };

  const toggleModel = async (activeModel: 'GLOBAL' | 'LOCAL') => {
    try {
      await fetch('/api/mqtt/federated/model/toggle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ activeModel })
      });
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 p-8 font-sans text-gray-900">
      <div className="max-w-4xl mx-auto space-y-8">
        
        <header className="bg-white p-6 rounded-lg shadow-md flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-gray-800">Federated Learning Dashboard</h1>
            <p className="text-gray-500 mt-1">Real-time smartphone spatial pose classification</p>
          </div>
          <div className="flex items-center space-x-2">
            <span className={`w-3 h-3 rounded-full ${connected ? 'bg-green-500' : 'bg-red-500'}`}></span>
            <span className="text-sm font-medium text-gray-600">{connected ? 'Telemetry Connected' : 'Disconnected'}</span>
          </div>
        </header>

        <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {Object.keys(telemetry).length === 0 ? (
             <div className="col-span-1 md:col-span-2 bg-white p-8 rounded-lg shadow-md text-center text-gray-500">
               <svg className="mx-auto h-12 w-12 text-gray-400 mb-4 animate-pulse" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
               </svg>
               Waiting for edge device telemetry...
             </div>
          ) : (
            Object.entries(telemetry).map(([clientId, data]) => (
              <div key={clientId} className="bg-white p-6 rounded-lg shadow-md border-t-4 border-blue-500 flex flex-col justify-between">
                <h2 className="text-lg font-bold text-gray-700 mb-4 uppercase tracking-wider">{clientId}</h2>
                <div className="flex flex-col items-center justify-center py-8 bg-blue-50 rounded-lg flex-grow">
                  <span className="text-4xl font-extrabold text-blue-600 text-center">{data.pose}</span>
                </div>
                <p className="text-xs text-gray-400 mt-4 text-right font-mono">
                  Last updated: {new Date(data.timestamp).toLocaleTimeString()}
                </p>
              </div>
            ))
          )}
        </section>

        <section className="bg-white p-6 rounded-lg shadow-md space-y-8">
          <div>
            <h2 className="text-xl font-bold text-gray-800 mb-4 border-b pb-2">Experiment Configuration</h2>
            <div className="flex flex-col sm:flex-row space-y-3 sm:space-y-0 sm:space-x-4">
              <button 
                onClick={() => sendCommand('START_TRAINING', false)}
                className="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 px-4 rounded-lg transition-colors shadow-sm cursor-pointer"
              >
                Start Training (32-bit Baseline)
              </button>
              <button 
                onClick={() => sendCommand('START_TRAINING', true)}
                className="flex-1 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-3 px-4 rounded-lg transition-colors shadow-sm cursor-pointer"
              >
                Start Training (8-bit Quantized)
              </button>
            </div>
            <p className="text-sm text-gray-500 mt-3">Initiates comparative training runs, resets models, and routes static data through the selected pipeline.</p>
          </div>

          <div>
            <h2 className="text-xl font-bold text-gray-800 mb-4 border-b pb-2">Active Classification Model (Live Evaluation)</h2>
            <div className="flex flex-col sm:flex-row space-y-3 sm:space-y-0 sm:space-x-4">
              <button 
                onClick={() => toggleModel('LOCAL')}
                className="flex-1 bg-gray-600 hover:bg-gray-700 text-white font-semibold py-3 px-4 rounded-lg transition-colors shadow-sm cursor-pointer"
              >
                Switch to Local Model
              </button>
              <button 
                onClick={() => toggleModel('GLOBAL')}
                className="flex-1 bg-teal-600 hover:bg-teal-700 text-white font-semibold py-3 px-4 rounded-lg transition-colors shadow-sm cursor-pointer"
              >
                Switch to Global Model
              </button>
            </div>
            <p className="text-sm text-gray-500 mt-3">Instantly switch the active classification model on the edge devices to compare local baseline vs. federated global accuracy.</p>
          </div>
        </section>

      </div>
    </div>
  );
}
