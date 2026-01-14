import React from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export default function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    // Preserve search params when redirecting to login
    return <Navigate to="/login" replace state={{ from: { pathname: location.pathname, search: location.search } }} />
  }

  return children
}
