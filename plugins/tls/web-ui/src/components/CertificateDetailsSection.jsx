import React, { useState } from 'react'
import {
  Box,
  Typography,
  Stack,
  Chip,
  Button,
  Collapse
} from '@mui/material'
import { Info, ExpandMore, ExpandLess } from '@mui/icons-material'

const CertificateDetailsSection = ({ certificateDetails }) => {
  const [sanExpanded, setSanExpanded] = useState(false)
  
  if (!certificateDetails) return null

  return (
    <Box>
      <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <Info color="primary" />
        Certificate Details
      </Typography>
      
      <Box sx={{ 
        border: '1px solid', 
        borderColor: 'divider', 
        borderRadius: 1, 
        p: 2,
        backgroundColor: 'grey.50'
      }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="subtitle2" color="text.secondary">Subject</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
              {certificateDetails.subjectStr || 'Unknown'}
            </Typography>
          </Box>
          
          <Box>
            <Typography variant="subtitle2" color="text.secondary">Issuer</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
              {certificateDetails.issuerStr || 'Unknown'}
            </Typography>
          </Box>
          
          <Box>
            <Typography variant="subtitle2" color="text.secondary">Type</Typography>
            <Chip label={certificateDetails.type || 'Unknown'} size="small" color="primary" />
          </Box>
          
          <Box>
            <Typography variant="subtitle2" color="text.secondary">Serial Number</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
              {certificateDetails.serialNumber || 'Unknown'}
            </Typography>
          </Box>
          
          <Box>
            <Typography variant="subtitle2" color="text.secondary">Validity Period</Typography>
            <Typography variant="body2">
              From: {certificateDetails.validFrom || 'Unknown'}
            </Typography>
            <Typography variant="body2">
              To: {certificateDetails.validTo || 'Unknown'}
            </Typography>
          </Box>
          
          <Box>
            <Typography variant="subtitle2" color="text.secondary">SHA-1 Fingerprint</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.75rem', wordBreak: 'break-all' }}>
              {certificateDetails.fingerprintSha1 || 'Unknown'}
            </Typography>
          </Box>
          
          {/* Subject Alternative Names */}
          {certificateDetails.subjectAltNames && 
           (certificateDetails.subjectAltNames.dns?.length > 0 ||
            certificateDetails.subjectAltNames.ip?.length > 0 ||
            certificateDetails.subjectAltNames.uri?.length > 0 ||
            certificateDetails.subjectAltNames.email?.length > 0 ||
            certificateDetails.subjectAltNames.dn?.length > 0) && (
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="subtitle2" color="text.secondary">
                  Subject Alternative Names
                </Typography>
                <Button
                  size="small"
                  onClick={() => setSanExpanded(!sanExpanded)}
                  endIcon={sanExpanded ? <ExpandLess /> : <ExpandMore />}
                  sx={{ minWidth: 'auto', textTransform: 'none' }}
                >
                  {sanExpanded ? 'Show Less' : 'Show More'}
                </Button>
              </Box>
              <Box
                sx={{
                  maxHeight: sanExpanded ? 'none' : '300px',
                  overflow: sanExpanded ? 'visible' : 'auto',
                  transition: 'max-height 0.3s ease-in-out'
                }}
              >
                <Stack spacing={1}>
                {certificateDetails.subjectAltNames.dns?.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                      DNS Names:
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
                      {certificateDetails.subjectAltNames.dns.map((dns, index) => (
                        <Chip key={index} label={dns} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Box>
                )}
                {certificateDetails.subjectAltNames.ip?.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                      IP Addresses:
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
                      {certificateDetails.subjectAltNames.ip.map((ip, index) => (
                        <Chip key={index} label={ip} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Box>
                )}
                {certificateDetails.subjectAltNames.uri?.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                      URIs:
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
                      {certificateDetails.subjectAltNames.uri.map((uri, index) => (
                        <Chip key={index} label={uri} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Box>
                )}
                {certificateDetails.subjectAltNames.email?.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                      Email Addresses:
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
                      {certificateDetails.subjectAltNames.email.map((email, index) => (
                        <Chip key={index} label={email} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Box>
                )}
                {certificateDetails.subjectAltNames.dn?.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                      Distinguished Names:
                    </Typography>
                    <Stack spacing={0.5}>
                      {certificateDetails.subjectAltNames.dn.map((dn, index) => (
                        <Box
                          key={index}
                          sx={{
                            p: 1,
                            border: '1px solid',
                            borderColor: 'divider',
                            borderRadius: 1,
                            backgroundColor: 'background.paper',
                            fontFamily: 'monospace',
                            fontSize: '0.7rem',
                            wordBreak: 'break-all',
                            whiteSpace: 'pre-wrap'
                          }}
                        >
                          {dn}
                        </Box>
                      ))}
                    </Stack>
                  </Box>
                )}
                </Stack>
              </Box>
            </Box>
          )}
        </Stack>
      </Box>
    </Box>
  )
}

export default CertificateDetailsSection
