import React from 'react'
import { Box, Stack } from '@mui/material'
import CertificateDetailsSection from './CertificateDetailsSection'
import CertificateVerificationSection from './CertificateVerificationSection'

const MobileCertificateSection = ({
  certificateDetails,
  verificationResult,
  isVerifying,
  onVerify,
  pemText
}) => {
  return (
    <Box sx={{ 
      display: { xs: 'block', md: 'none' }, // Show on mobile, hide on desktop
      mt: 2
    }}>
      <Stack spacing={2}>
        <CertificateDetailsSection certificateDetails={certificateDetails} />
        <CertificateVerificationSection
          verificationResult={verificationResult}
          isVerifying={isVerifying}
          onVerify={onVerify}
          pemText={pemText}
        />
      </Stack>
    </Box>
  )
}

export default MobileCertificateSection
