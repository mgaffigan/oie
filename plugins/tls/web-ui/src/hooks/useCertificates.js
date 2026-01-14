import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchSystemCertificates, fetchTrustedCertificates, fetchLocalCertificates } from '../services/tlsService'

function normalize(text) {
  return (text || '').toString().toLowerCase()
}

/**
 * Hook to manage certificates with preloading of all tabs
 * @param {string} tabKey - The active tab key ('native', 'trusted', or 'private')
 * @returns {Object} Certificate data and utilities
 */
export default function useCertificates(tabKey = 'native') {
  
  // Store certificates per tab
  const [certificatesByTab, setCertificatesByTab] = useState({
    native: [],
    trusted: [],
    private: [],
  })
  
  // Track loading state per tab
  const [loadingByTab, setLoadingByTab] = useState({
    native: false,
    trusted: false,
    private: false,
  })
  
  // Track error state per tab
  const [errorByTab, setErrorByTab] = useState({
    native: '',
    trusted: '',
    private: '',
  })

  // Use refs to track state without causing re-renders or dependency issues
  const certificatesByTabRef = useRef(certificatesByTab)
  const loadingByTabRef = useRef(loadingByTab)
  
  // Keep refs in sync with state
  useEffect(() => {
    certificatesByTabRef.current = certificatesByTab
  }, [certificatesByTab])
  
  useEffect(() => {
    loadingByTabRef.current = loadingByTab
  }, [loadingByTab])

  // Map tab keys to fetch functions
  const fetchFunctions = {
    native: fetchSystemCertificates,
    trusted: fetchTrustedCertificates,
    private: fetchLocalCertificates,
  }

  const fetchByTab = useCallback(async (key, force = false) => {
    // Check current state using refs to avoid stale closures
    if (!force && (loadingByTabRef.current[key] || certificatesByTabRef.current[key].length > 0)) {
      return
    }

    setLoadingByTab((prev) => ({ ...prev, [key]: true }))
    setErrorByTab((prev) => ({ ...prev, [key]: '' }))
    
    try {
      const fetchFn = fetchFunctions[key]
      if (!fetchFn) {
        throw new Error(`Unknown tab key: ${key}`)
      }
      
      const data = await fetchFn()
      setCertificatesByTab((prev) => ({ ...prev, [key]: data }))
    } catch (e) {
      // Service already shows notification, just update error state
      setErrorByTab((prev) => ({ ...prev, [key]: `Failed to load certificates` }))
    } finally {
      setLoadingByTab((prev) => ({ ...prev, [key]: false }))
    }
  }, []) // Empty dependencies - use refs to access current state

  // Preload all certificate types on mount
  useEffect(() => {
    const tabKeys = ['native', 'trusted', 'private']
    
    // Fetch all certificate types in parallel
    Promise.allSettled(
      tabKeys.map(async (key) => {
        // Only fetch if not already loaded or loading
        if (!loadingByTabRef.current[key] && certificatesByTabRef.current[key].length === 0) {
          await fetchByTab(key)
        }
      })
    )
  }, [fetchByTab]) // fetchByTab is stable (useCallback with empty deps), but included for correctness

  // Fetch certificates when tab changes (fallback for manual refresh)
  useEffect(() => {
    if (tabKey && fetchFunctions[tabKey]) {
      // Only fetch if not already loaded (preload may have already loaded it)
      if (!loadingByTabRef.current[tabKey] && certificatesByTabRef.current[tabKey].length === 0) {
        fetchByTab(tabKey)
      }
    }
  }, [tabKey, fetchByTab])

  // Get current tab's certificates
  const currentTabCertificates = certificatesByTab[tabKey] || []
  const currentLoading = loadingByTab[tabKey] || false
  const currentError = errorByTab[tabKey] || ''

  // Combine all certificates for counts and filtering
  const all = useMemo(() => {
    return [
      ...certificatesByTab.native,
      ...certificatesByTab.trusted,
      ...certificatesByTab.private,
    ]
  }, [certificatesByTab])

  const filterBy = (storeKey, search) => {
    const q = normalize(search)
    const tabCertificates = certificatesByTab[storeKey] || []
    return tabCertificates.filter((c) => {
      if (!q) return true
      return normalize(c.alias).includes(q) || normalize(c.name).includes(q) || normalize(c.subject).includes(q)
    })
  }

  const counts = useMemo(() => ({
    native: certificatesByTab.native.length,
    trusted: certificatesByTab.trusted.length,
    private: certificatesByTab.private.length,
  }), [certificatesByTab])

  // Refetch function for current tab
  const refetch = async () => {
    if (tabKey && fetchFunctions[tabKey]) {
      // Clear current tab's data and force reload
      setCertificatesByTab((prev) => ({ ...prev, [tabKey]: [] }))
      await fetchByTab(tabKey, true)
    }
  }

  // Get certificates by store name (for update operations)
  const getCertificatesByStore = useCallback((store) => {
    // Map store names to tab keys
    const storeToTabMap = {
      'trusted': 'trusted',
      'private': 'private',
      'native': 'native'
    }
    const tabKey = storeToTabMap[store]
    return tabKey ? certificatesByTab[tabKey] || [] : []
  }, [certificatesByTab])

  return { 
    all, 
    loading: currentLoading, 
    error: currentError, 
    counts, 
    filterBy, 
    refetch,
    getCertificatesByStore,
    certificatesByTab // Also expose directly for convenience
  }
}


