import React from 'react'
import {
  Box,
  Typography,
  Stack,
  Button,
  Alert,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItem,
  ListItemText,
  CircularProgress
} from '@mui/material'
import {
  Security,
  CheckCircle,
  Error,
  ExpandMore
} from '@mui/icons-material'

const CertificateVerificationSection = ({
  verificationResult,
  isVerifying,
  onVerify,
  pemText
}) => {
  return (
    <Box>
      <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Security color="primary" />
          Certificate Verification
        </Typography>
      </Stack>

      {verificationResult && (
        <Box>
          {verificationResult.success ? (
            <Alert severity="success" sx={{ mb: 2 }}>
              Certificate verification completed successfully!
            </Alert>
          ) : (
            <Alert severity="error" sx={{ mb: 2 }}>
              {verificationResult.error}
            </Alert>
          )}

          {verificationResult.success && (
            <Stack spacing={2}>
              {/* Chain Validation Results */}
              {verificationResult.chainValidation && (
                <Accordion>
                  <AccordionSummary expandIcon={<ExpandMore />}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      {verificationResult.chainValidation.isValid ? (
                        <CheckCircle color="success" />
                      ) : (
                        <Error color="error" />
                      )}
                      <Typography variant="h6">
                        Chain Validation {verificationResult.chainValidation.isValid ? 'Passed' : 'Failed'}
                      </Typography>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>
                    {verificationResult.chainValidation.errors.length > 0 && (
                      <Box sx={{ mb: 2 }}>
                        <Typography variant="subtitle2" color="error" gutterBottom>
                          Errors:
                        </Typography>
                        <List dense>
                          {verificationResult.chainValidation.errors.map((error, index) => (
                            <ListItem key={index}>
                              <ListItemText primary={error} />
                            </ListItem>
                          ))}
                        </List>
                      </Box>
                    )}
                    {verificationResult.chainValidation.warnings.length > 0 && (
                      <Box sx={{ mb: 2 }}>
                        <Typography variant="subtitle2" color="warning.main" gutterBottom>
                          Warnings:
                        </Typography>
                        <List dense>
                          {verificationResult.chainValidation.warnings.map((warning, index) => (
                            <ListItem key={index}>
                              <ListItemText primary={warning} />
                            </ListItem>
                          ))}
                        </List>
                      </Box>
                    )}
                    {verificationResult.chainValidation.details.length > 0 && (
                      <Box>
                        <Typography variant="subtitle2" gutterBottom>
                          Details:
                        </Typography>
                        <List dense>
                          {verificationResult.chainValidation.details.map((detail, index) => (
                            <ListItem key={index}>
                              <ListItemText primary={detail} />
                            </ListItem>
                          ))}
                        </List>
                      </Box>
                    )}
                  </AccordionDetails>
                </Accordion>
              )}

              {/* Private Key Validation */}
              {verificationResult.keyValidation && (
                <Accordion>
                  <AccordionSummary expandIcon={<ExpandMore />}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      {verificationResult.keyValidation.isValid ? (
                        <CheckCircle color="success" />
                      ) : (
                        <Error color="error" />
                      )}
                      <Typography variant="h6">
                        Private Key Validation {verificationResult.keyValidation.isValid ? 'Passed' : 'Failed'}
                      </Typography>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Alert 
                      severity={verificationResult.keyValidation.isValid ? 'success' : 'error'}
                    >
                      {verificationResult.keyValidation.message}
                    </Alert>
                  </AccordionDetails>
                </Accordion>
              )}

              {/* Certificate Chain Details */}
              {verificationResult.chainDetails && verificationResult.chainDetails.length > 1 && (
                <Accordion>
                  <AccordionSummary expandIcon={<ExpandMore />}>
                    <Typography variant="h6">
                      Certificate Chain ({verificationResult.chainDetails.length} certificates)
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <List>
                      {verificationResult.chainDetails.map((cert, index) => (
                        <ListItem key={index} sx={{ flexDirection: 'column', alignItems: 'flex-start' }}>
                          <Typography variant="subtitle2" gutterBottom>
                            {cert.type} (Certificate #{cert.index})
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Subject: {cert.subject}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Issuer: {cert.issuer}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Valid: {cert.validFrom} - {cert.validTo}
                          </Typography>
                        </ListItem>
                      ))}
                    </List>
                  </AccordionDetails>
                </Accordion>
              )}
            </Stack>
          )}
        </Box>
      )}
    </Box>
  )
}

export default CertificateVerificationSection
