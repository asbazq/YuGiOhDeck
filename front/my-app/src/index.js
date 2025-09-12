import React from 'react';
import ReactDOM from 'react-dom/client';
import './styles/index.css';
import App from './App';
import QueueAdminPage from './QueueAdminPage';
import QueueApp from './components/QueueApp';
import reportWebVitals from './reportWebVitals';
import LimitPage from './components/LimitPage';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

const root = ReactDOM.createRoot(document.getElementById('root'));

const isAdmin = window.location.pathname.startsWith('/admin/queue');

const app = isAdmin ? (
  <QueueAdminPage />
) : (
  <BrowserRouter>
    <QueueApp>
      <Routes>
        <Route path="/admin/queue/:qid?" element={<QueueAdminPage />} />
        <Route path="/limit" element={<LimitPage />} />
        <Route path="/" element={<App />} />
      </Routes>
    </QueueApp>
  </BrowserRouter>
);

root.render(
  <React.StrictMode>
    {app}
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
