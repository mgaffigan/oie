import { useState, useEffect } from 'react'
import { fetchCertificates } from '../services/tlsService'

export const useAliasEdit = (currentAlias, currentStore, currentCertificates = null) => {
  
  // State management
  const [newAlias, setNewAlias] = useState('')
  const [existingCertificates, setExistingCertificates] = useState([])
  const [aliasWarning, setAliasWarning] = useState(null)
  const [loading, setLoading] = useState(false)
  const [apiError, setApiError] = useState(null)
  const [existingCertificateInfo, setExistingCertificateInfo] = useState(null)

  // Load existing certificates to check for alias conflicts
  const loadExistingCertificates = async () => {
    try {
      // Use provided currentCertificates if available, otherwise fetch
      if (currentCertificates && Array.isArray(currentCertificates)) {
        setExistingCertificates(currentCertificates)
      } else {
        const certificates = await fetchCertificates()
        setExistingCertificates(certificates)
      }
    } catch (error) {
      // Service already shows notification, no need to show again
    }
  }

  // Check if alias already exists within the same store (excluding current certificate)
  const checkAliasExists = (aliasToCheck) => {
    if (!aliasToCheck.trim()) {
      setAliasWarning(null)
      setExistingCertificateInfo(null)
      return false
    }

    // Only check certificates in the same store, excluding the current certificate
    const existingCert = existingCertificates.find(cert => 
      cert.store === currentStore &&
      cert.alias.toLowerCase() === aliasToCheck.toLowerCase() && 
      cert.alias.toLowerCase() !== currentAlias.toLowerCase()
    )
    
    if (existingCert) {
      setAliasWarning('This alias is already in use in this store')
      setExistingCertificateInfo(existingCert)
      return true
    } else {
      setAliasWarning(null)
      setExistingCertificateInfo(null)
      return false
    }
  }

  // Handle alias change
  const handleAliasChange = (e) => {
    const newValue = e.target.value
    setNewAlias(newValue)
    checkAliasExists(newValue)
  }

  // Validation logic
  const validate = () => {
    if (!newAlias.trim()) {
      setApiError('Alias is required.')
      return false
    }
    if (newAlias.trim() === currentAlias) {
      setApiError('New alias must be different from current alias.')
      return false
    }
    return true
  }

  // Initialize with current alias and reset warning state
  useEffect(() => {
    setNewAlias(currentAlias)
    setAliasWarning(null)
    setExistingCertificateInfo(null)
  }, [currentAlias])

  // Load existing certificates on mount or when currentCertificates changes
  useEffect(() => {
    loadExistingCertificates()
    // Reset warning state when certificates are reloaded (dialog opened fresh)
    setAliasWarning(null)
    setExistingCertificateInfo(null)
  }, [currentCertificates])

  return {
    // State
    newAlias,
    aliasWarning,
    loading,
    apiError,
    existingCertificates,
    existingCertificateInfo,
    
    // Actions
    setLoading,
    setApiError,
    
    // Handlers
    handleAliasChange,
    validate,
    checkAliasExists,
    loadExistingCertificates
  }
}
