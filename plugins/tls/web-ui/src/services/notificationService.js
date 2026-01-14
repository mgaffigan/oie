/**
 * Notification Service - Can be used outside React components
 * This service provides a way to show notifications from services and utils
 * It uses an event emitter pattern to communicate with the NotificationProvider
 */

class NotificationService {
  constructor() {
    this.listeners = []
  }

  // Subscribe to notifications (called by NotificationProvider)
  subscribe(callback) {
    this.listeners.push(callback)
    // Return unsubscribe function
    return () => {
      this.listeners = this.listeners.filter(listener => listener !== callback)
    }
  }

  // Show notification (can be called from anywhere)
  showNotification(message, severity = 'info') {
    this.listeners.forEach(listener => {
      listener(message, severity)
    })
  }

  showSuccess(message) {
    this.showNotification(message, 'success')
  }

  showError(message) {
    this.showNotification(message, 'error')
  }

  showWarning(message) {
    this.showNotification(message, 'warning')
  }

  showInfo(message) {
    this.showNotification(message, 'info')
  }
}

// Export singleton instance
export const notificationService = new NotificationService()

