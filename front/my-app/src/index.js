import React from 'react';
import ReactDOM from 'react-dom/client';
import './styles/index.css';
import App from './App';
import QueueAdminPage from './QueueAdminPageModern';
import QueueApp from './components/QueueApp';
import reportWebVitals from './reportWebVitals';
import LimitPage from './components/LimitPageModern';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

const root = ReactDOM.createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <BrowserRouter>
      <QueueApp>
        <Routes>
          <Route path="/admin/queue/:qid?" element={<QueueAdminPage />} />
          <Route path="/limit" element={<LimitPage />} />
          <Route path="/" element={<App />} />
        </Routes>
      </QueueApp>
    </BrowserRouter>
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
