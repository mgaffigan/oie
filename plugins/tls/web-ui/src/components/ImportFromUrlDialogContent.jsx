import React, { useState, useEffect, useRef } from 'react'
import {
  Box,
  Stack,
  Button,
  TextField,
  Alert,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
} from '@mui/material'
import { fetchRemoteCertificates, updateCertificates } from '../services/tlsService.js'
import TrustedCertificateImportForm from './TrustedCertificateImportForm'
import CertificateChainSelector from './CertificateChainSelector'
import ConfirmReplaceCertificateDialog from './ConfirmReplaceCertificateDialog'
import { verifyCertificate } from '../utils/verificationUtils.js'

export default function ImportFromUrlDialogContent({
  targetStore = 'trusted',
  currentCertificates = null,
  onCancel,
  onSuccess,
}) {
  const [url, setUrl] = useState('')
  const [urlError, setUrlError] = useState('')
  const [loading, setLoading] = useState(false)
  const [fetchError, setFetchError] = useState(null)
  const [certificates, setCertificates] = useState([])
  const [selectedCertificateIndex, setSelectedCertificateIndex] = useState(null)
  const [selectedCertificatePem, setSelectedCertificatePem] = useState(null)
  const [importLoading, setImportLoading] = useState(false)
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)
  const [showValidationDialog, setShowValidationDialog] = useState(false)
  const [validationError, setValidationError] = useState(null)
  const [existingCertificateInfo, setExistingCertificateInfo] = useState(null)
  const [confirmAlias, setConfirmAlias] = useState('')
  const formRef = useRef(null)

  const validateUrl = (urlValue) => {
    if (!urlValue.trim()) {
      setUrlError('URL is required')
      return false
    }
    if (!urlValue.startsWith('https://')) {
      setUrlError('URL must start with https://')
      return false
    }
    try {
      new URL(urlValue)
      setUrlError('')
      return true
    } catch (e) {
      setUrlError('Invalid URL format')
      return false
    }
  }

  const handleUrlChange = (e) => {
    const newUrl = e.target.value
    setUrl(newUrl)
    if (urlError) {
      validateUrl(newUrl)
    }
  }

  const handleFetchCertificates = async () => {
    if (!validateUrl(url)) {
      return
    }

    setLoading(true)
    setFetchError(null)
    setCertificates([])
    setSelectedCertificateIndex(null)
    setSelectedCertificatePem(null)

    try {
      const fetchedCerts = await fetchRemoteCertificates(url)
      if (fetchedCerts.length === 0) {
        setFetchError('No certificates found at the specified URL')
        setLoading(false)
        return
      }
      setCertificates(fetchedCerts)
      // Auto-select first certificate if available
      if (fetchedCerts.length > 0) {
        setSelectedCertificateIndex(0)
        setSelectedCertificatePem(fetchedCerts[0].certificate)
      }
    } catch (error) {
      setFetchError(error.message || 'Failed to fetch certificates from URL')
    } finally {
      setLoading(false)
    }
  }

  const handleCertificateSelect = (index) => {
    setSelectedCertificateIndex(index)
    const selectedCert = certificates[index]
    setSelectedCertificatePem(selectedCert.certificate)
  }

  // Update selected certificate PEM when index changes
  useEffect(() => {
    if (selectedCertificateIndex !== null && certificates[selectedCertificateIndex]) {
      setSelectedCertificatePem(certificates[selectedCertificateIndex].certificate)
      setImportLoading(false) // Reset loading when certificate selection changes
    }
  }, [selectedCertificateIndex, certificates])

  const performFinalVerification = async (pemText) => {
    try {
      const verificationResult = await verifyCertificate(pemText, null)
      
      if (!verificationResult.success) {
        setValidationError(verificationResult.error || 'Certificate validation failed')
        setShowValidationDialog(true)
        return false
      }
      return true
    } catch (error) {
      setValidationError('Certificate validation failed: ' + error.message)
      setShowValidationDialog(true)
      return false
    }
  }

  const performImport = async (alias, pemText) => {
    setImportLoading(true)
    try {
      const result = await updateCertificates('trusted', {
        alias,
        pemText,
      }, currentCertificates)
      if (result.success) {
        setImportLoading(false)
        onSuccess?.(result.data)
      } else {
        setImportLoading(false)
        if (formRef.current) {
          formRef.current.setApiError(result.error || 'Failed to import certificate')
        }
      }
    } catch (error) {
      setImportLoading(false)
      if (formRef.current) {
        formRef.current.setApiError(error.message || 'Failed to import certificate')
      }
    }
  }

  const handleSubmit = async () => {
    if (!formRef.current) return

    if (!formRef.current.validate()) return

    // Check if alias already exists
    const aliasExists = formRef.current.checkAliasExists()
    if (aliasExists) {
      // Find the existing certificate info for the confirmation dialog
      const alias = formRef.current.alias
      setConfirmAlias(alias)
      const existingCert = currentCertificates?.find(c => 
        c.alias.toLowerCase() === alias.toLowerCase() && 
        c.store === targetStore
      )
      setExistingCertificateInfo(existingCert || null)
      setShowConfirmDialog(true)
      return
    }

    // Final verification before import
    const verificationPassed = await performFinalVerification(formRef.current.pemText)
    if (!verificationPassed) return

    // Proceed with import if verification passes
    await performImport(formRef.current.alias, formRef.current.pemText)
  }

  const handleConfirmReplace = async () => {
    setShowConfirmDialog(false)
    
    if (!formRef.current) return

    // Final verification before import
    const verificationPassed = await performFinalVerification(formRef.current.pemText)
    if (!verificationPassed) return

    // Proceed with import if verification passes
    await performImport(formRef.current.alias, formRef.current.pemText)
  }

  return (
    <Box sx={{ 
      pt: 0.5,
      maxHeight: '80vh',
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden'
    }}>
      {/* URL Input Section */}
      <Box sx={{ mb: 3 }}>
        <Stack direction="row" spacing={2} alignItems="flex-start">
          <TextField
            label="URL"
            placeholder="https://example.com"
            value={url}
            onChange={handleUrlChange}
            onBlur={() => validateUrl(url)}
            error={!!urlError}
            helperText={urlError || 'Enter a valid HTTPS URL to fetch certificates'}
            fullWidth
            disabled={loading}
            autoFocus
          />
          <Button
            variant="contained"
            size="small"
            onClick={handleFetchCertificates}
            disabled={loading || !url.trim() || !!urlError}
            sx={{ minWidth: 120, mt: 1 }}
          >
            {loading ? (
              <>
                <CircularProgress size={14} sx={{ mr: 0.75 }} />
                Fetching...
              </>
            ) : (
              'Fetch Certificates'
            )}
          </Button>
        </Stack>
        
        {fetchError && (
          <Alert severity="error" sx={{ mt: 2 }}>{fetchError}</Alert>
        )}
      </Box>

      {/* Certificate List and Import Details - Vertical Layout */}
      {certificates.length > 0 && (
        <Box sx={{ 
          flex: 1,
          overflow: 'auto',
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 3
        }}>
          {/* Top Section - Certificate List */}
          <CertificateChainSelector
            certificates={certificates}
            selectedIndex={selectedCertificateIndex}
            onSelect={handleCertificateSelect}
            loading={loading}
          />

          {/* Bottom Section - Import Certificate Details */}
          <Box sx={{ 
            flex: 1,
            minHeight: 0,
            overflow: 'auto'
          }}>
            {selectedCertificatePem ? (
              <TrustedCertificateImportForm
                ref={formRef}
                key={selectedCertificateIndex}
                currentCertificates={currentCertificates}
                initialPemText={selectedCertificatePem}
                readOnlyPem={true}
              />
            ) : (
              <Alert severity="info">
                Select a certificate from the list to view details and import
              </Alert>
            )}
          </Box>
        </Box>
      )}

      {/* Fixed buttons at bottom - only show when certificate is selected */}
      {certificates.length > 0 && selectedCertificatePem && (
        <Stack 
          direction="row" 
          spacing={1} 
          justifyContent="flex-end" 
          sx={{ 
            pt: 2, 
            borderTop: '1px solid', 
            borderColor: 'divider',
            mt: 'auto',
            flexShrink: 0
          }}
        >
          <Button onClick={onCancel} disabled={importLoading}>
            Cancel
          </Button>
          <Button 
            variant="contained" 
            onClick={handleSubmit}
            disabled={importLoading}
          >
            {importLoading ? 'Importing...' : 'Import Certificate'}
          </Button>
        </Stack>
      )}

      {/* Confirmation Dialog for Replacing Existing Certificate */}
      <ConfirmReplaceCertificateDialog
        open={showConfirmDialog}
        onClose={() => setShowConfirmDialog(false)}
        onConfirm={handleConfirmReplace}
        alias={confirmAlias}
        store={targetStore}
        loading={importLoading}
        existingCertificateInfo={existingCertificateInfo}
      />

      {/* Validation Error Dialog */}
      <Dialog
        open={showValidationDialog}
        onClose={() => setShowValidationDialog(false)}
        aria-labelledby="validation-dialog-title"
        aria-describedby="validation-dialog-description"
      >
        <DialogTitle id="validation-dialog-title">
          Certificate Validation Failed
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="validation-dialog-description">
            {validationError}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowValidationDialog(false)}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

    </Box>
  )
}

