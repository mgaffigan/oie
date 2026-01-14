import React, { createContext, useContext, useState, useEffect } from 'react'
import { Snackbar, Alert } from '@mui/material'
import { notificationService } from '../services/notificationService'

const NotificationContext = createContext()

export const useNotification = () => {
  const context = useContext(NotificationContext)
  if (!context) {
    throw new Error('useNotification must be used within a NotificationProvider')
  }
  return context
}

export const NotificationProvider = ({ children }) => {
  const [notification, setNotification] = useState({
    open: false,
    message: '',
    severity: 'info' // 'success', 'error', 'warning', 'info'
  })

  const showNotification = (message, severity = 'info') => {
    setNotification({
      open: true,
      message,
      severity
    })
  }

  const hideNotification = () => {
    setNotification(prev => ({
      ...prev,
      open: false
    }))
  }

  const showSuccess = (message) => showNotification(message, 'success')
  const showError = (message) => showNotification(message, 'error')
  const showWarning = (message) => showNotification(message, 'warning')
  const showInfo = (message) => showNotification(message, 'info')

  // Subscribe to notification service so services/utils can trigger notifications
  useEffect(() => {
    const unsubscribe = notificationService.subscribe((message, severity) => {
      showNotification(message, severity)
    })
    return unsubscribe
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <NotificationContext.Provider
      value={{
        showNotification,
        showSuccess,
        showError,
        showWarning,
        showInfo,
        hideNotification
      }}
    >
      {children}
      <Snackbar
        open={notification.open}
        autoHideDuration={6000}
        onClose={hideNotification}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert
          onClose={hideNotification}
          severity={notification.severity}
          sx={{ width: '100%' }}
        >
          {notification.message}
        </Alert>
      </Snackbar>
    </NotificationContext.Provider>
  )
}
