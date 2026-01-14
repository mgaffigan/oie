import React, { useState, useEffect, forwardRef, useImperativeHandle } from 'react'
import {
  Box,
  Stack,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
} from '@mui/material'
import { useCertificateImport } from '../hooks/useCertificateImport'
import CertificateDetailsSection from './CertificateDetailsSection'
import CertificateVerificationSection from './CertificateVerificationSection'
import UserInputsSection from './UserInputsSection'
import MobileCertificateSection from './MobileCertificateSection'
import ConfirmReplaceCertificateDialog from './ConfirmReplaceCertificateDialog'
import { updateCertificates } from '../services/tlsService.js'
import { verifyCertificate } from '../utils/verificationUtils.js'

const ImportPrivateCertificateDialog = forwardRef(function ImportPrivateCertificateDialog({
  currentCertificates = null,
  onCancel,
  onSubmit,
  onSuccess,
  initialPemText = null,
  readOnlyPem = false,
  hideButtons = false,
}, ref) {
  const targetStore = 'private'
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)
  const [showValidationDialog, setShowValidationDialog] = useState(false)
  const [validationError, setValidationError] = useState(null)
  
  const {
    // State
    alias,
    pemText,
    privateKeyText,
    file,
    privateKeyFile,
    loading,
    apiError,
    errors,
    certificateDetails,
    verificationResult,
    isVerifying,
    existingCertificates,
    aliasWarning,
    existingCertificateInfo,
    
    // Refs
    fileInputRef,
    privateKeyFileInputRef,
    certFileAccept,
    keyFileAccept,
    
    // Actions
    setLoading,
    setApiError,
    setPemText,
    parseCertificateDetails,
    
    // Handlers
    handleVerifyCertificate,
    handleFileUpload,
    handlePrivateKeyFileUpload,
    handlePemTextChange,
    handlePrivateKeyTextChange,
    handleAliasChange,
    validate,
    loadExistingCertificates,
    checkAliasExists
  } = useCertificateImport(targetStore, currentCertificates)

  // Load existing certificates on component mount
  useEffect(() => {
    loadExistingCertificates()
  }, [loadExistingCertificates])

  // Pre-populate PEM text if initialPemText is provided
  useEffect(() => {
    if (initialPemText && initialPemText.trim()) {
      // Always update when initialPemText changes (for URL import flow)
      setPemText(initialPemText)
      parseCertificateDetails(initialPemText)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialPemText])

  // Reusable verification function
  const performFinalVerification = async () => {
    try {
      const privateKeyPem = privateKeyText.trim() ? privateKeyText : null
      
      const verificationResult = await verifyCertificate(pemText, privateKeyPem)
      
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

  const handleSubmit = async () => {
    if (!validate()) return

    // Check if alias already exists
    const aliasExists = checkAliasExists(alias)
    if (aliasExists) {
      setShowConfirmDialog(true)
      return
    }

    // Final verification before import
    const verificationPassed = await performFinalVerification()
    if (!verificationPassed) return

    // Proceed with import if verification passes
    await performImport()
  }

  const performImport = async () => {
    setLoading(true)
    setApiError(null)
    try {
      const result = await updateCertificates(targetStore, {
        alias,
        pemText,
        privateKeyText,
      }, currentCertificates)
      if (result.success) {
        onSuccess?.(result.data)
        onSubmit?.()
      } else {
        setApiError(result.error || 'Failed to import certificate')
      }
    } catch (error) {
      setApiError(error.message || 'Failed to import certificate')
    } finally {
      setLoading(false)
    }
  }

  const handleConfirmReplace = async () => {
    setShowConfirmDialog(false)
    
    // Final verification before import
    const verificationPassed = await performFinalVerification()
    if (!verificationPassed) return

    // Proceed with import if verification passes
    await performImport()
  }

  const handleCancelReplace = () => {
    setShowConfirmDialog(false)
  }

  // Determine if import button should be disabled
  const isImportDisabled = () => {
    // Disable if loading
    if (loading) return true
    
    // Disable if there are validation errors
    if (errors.pemText || errors.alias || errors.privateKeyText || errors.file || errors.privateKeyFile) {
      return true
    }
    
    // Disable if verification has been attempted and failed
    if (verificationResult && verificationResult.success === false) {
      return true
    }
    
    // Disable if required fields are missing
    if (!pemText.trim() || !alias.trim() || (targetStore === 'private' && !privateKeyText.trim())) {
      return true
    }
    
    return false
  }

  // Expose handleSubmit and loading state via ref
  useImperativeHandle(ref, () => ({
    handleSubmit,
    loading
  }))

  return (
    <Box sx={{ 
      pt: 0.5,
      maxHeight: '80vh',
      display: 'flex',
      flexDirection: 'column'
    }}>
      <Box sx={{ 
        flex: 1,
        overflow: 'auto',
        minHeight: 0,
        display: 'flex',
        gap: 3
      }}>

        {/* Left Column - User Inputs */}
        <UserInputsSection
          alias={alias}
          pemText={pemText}
          privateKeyText={privateKeyText}
          file={file}
          privateKeyFile={privateKeyFile}
          apiError={apiError}
          errors={errors}
          targetStore={targetStore}
          aliasWarning={aliasWarning}
          fileInputRef={fileInputRef}
          privateKeyFileInputRef={privateKeyFileInputRef}
          certFileAccept={certFileAccept}
          keyFileAccept={keyFileAccept}
          handleAliasChange={handleAliasChange}
          handlePemTextChange={handlePemTextChange}
          handlePrivateKeyTextChange={handlePrivateKeyTextChange}
          handleFileUpload={handleFileUpload}
          handlePrivateKeyFileUpload={handlePrivateKeyFileUpload}
          setApiError={setApiError}
          readOnlyPem={readOnlyPem}
          showPrivateKeyFields={true}
        />
        {/* Right Column - Certificate Details & Verification */}
        <Box sx={{ 
          flex: 1,
          minWidth: 0,
          display: { xs: 'none', md: 'block' } // Hide on mobile, show on desktop
        }}>
          <Stack spacing={2}>
            <CertificateDetailsSection certificateDetails={certificateDetails} />
            <CertificateVerificationSection
              verificationResult={verificationResult}
              isVerifying={isVerifying}
              onVerify={handleVerifyCertificate}
              pemText={pemText}
            />
          </Stack>
        </Box>

        {/* Mobile Certificate Details & Verification */}
        <MobileCertificateSection
          certificateDetails={certificateDetails}
          verificationResult={verificationResult}
          isVerifying={isVerifying}
          onVerify={handleVerifyCertificate}
          pemText={pemText}
        />
      </Box>
      
      {/* Fixed buttons at bottom */}
      {!hideButtons && (
        <Stack 
          direction="row" 
          spacing={1} 
          justifyContent="flex-end" 
          sx={{ 
            pt: 2, 
            borderTop: '1px solid', 
            borderColor: 'divider',
            mt: 'auto'
          }}
        >
          <Button onClick={onCancel} disabled={loading}>
            Cancel
          </Button>
          <Button 
            variant="contained" 
            onClick={handleSubmit} 
            disabled={isImportDisabled()}
          >
            {loading ? 'Importing...' : 'Import Key Pair'}
          </Button>
        </Stack>
      )}

      {/* Confirmation Dialog for Replacing Existing Certificate */}
      <ConfirmReplaceCertificateDialog
        open={showConfirmDialog}
        onClose={handleCancelReplace}
        onConfirm={handleConfirmReplace}
        alias={alias}
        store={targetStore}
        loading={loading}
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
})

export default ImportPrivateCertificateDialog

