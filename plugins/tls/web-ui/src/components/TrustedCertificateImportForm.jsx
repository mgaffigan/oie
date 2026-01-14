import React, { useEffect, useImperativeHandle, forwardRef } from 'react'
import {
  Box,
  Stack
} from '@mui/material'
import { useCertificateImport } from '../hooks/useCertificateImport'
import CertificateDetailsSection from './CertificateDetailsSection'
import CertificateVerificationSection from './CertificateVerificationSection'
import UserInputsSection from './UserInputsSection'
import MobileCertificateSection from './MobileCertificateSection'

const TrustedCertificateImportForm = forwardRef(function TrustedCertificateImportForm({
  currentCertificates = null,
  initialPemText = null,
  readOnlyPem = false
}, ref) {
  const targetStore = 'trusted'
  
  const {
    // State
    alias,
    pemText,
    privateKeyText,
    file,
    privateKeyFile,
    apiError,
    errors,
    certificateDetails,
    verificationResult,
    isVerifying,
    existingCertificates,
    aliasWarning,
    
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

  // Expose form state and methods via ref
  useImperativeHandle(ref, () => ({
    alias,
    pemText,
    validate,
    checkAliasExists: () => checkAliasExists(alias),
    apiError,
    setApiError
  }))

  return (
    <Box sx={{ 
      display: 'flex',
      gap: 3,
      width: '100%'
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
        showPrivateKeyFields={false}
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
  )
})

export default TrustedCertificateImportForm

