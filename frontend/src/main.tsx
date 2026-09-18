import React from 'react';
import ReactDOM from 'react-dom/client';
import 'antd/dist/reset.css';
import './styles/tokens.css';
import './styles/reset.css';
import './styles/global.css';
import App from './App';

const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('root element missing');
}

ReactDOM.createRoot(rootEl).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);