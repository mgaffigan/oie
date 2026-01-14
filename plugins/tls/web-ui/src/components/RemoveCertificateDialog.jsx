import React, { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Box,
  Typography,
  Paper,
  Grid,
  Alert,
  Stack
} from '@mui/material'
import { Warning } from '@mui/icons-material'
import { removeCertificate } from '../services/tlsService'
import ChannelsInUseWarning from './ChannelsInUseWarning'

export default function RemoveCertificateDialog({ 
  open, 
  onClose, 
  certificate, 
  currentCertificates = null,
  onSuccess 
}) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  
  const handleRemove = async () => {
    if (!certificate) return

    setLoading(true)
    setError(null)
    
    try {
      const result = await removeCertificate(certificate.store, certificate.alias, currentCertificates)
      if (result.success) {
        onSuccess?.(result.data)
        onClose()
      } else {
        setError(result.error || 'Failed to remove certificate')
      }
    } catch (error) {
      setError(error.message || 'Failed to remove certificate')
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setError(null)
    onClose()
  }

  if (!certificate) return null

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} alignItems="center">
          <Warning color="error" />
          <Typography variant="h6">Remove Certificate</Typography>
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 1 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {/* Certificate Info Display */}
          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <Typography variant="h6" gutterBottom>Certificate Information</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="body2" color="text.secondary">Alias</Typography>
                <Typography variant="body1">{certificate.alias}</Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Typography variant="body2" color="text.secondary">Store</Typography>
                <Typography variant="body1" sx={{ textTransform: 'capitalize' }}>
                  {certificate.store}
                </Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="body2" color="text.secondary">Subject</Typography>
                <Typography variant="body1" sx={{ wordBreak: 'break-all' }}>
                  {certificate.subject}
                </Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="body2" color="text.secondary">Issuer</Typography>
                <Typography variant="body1" sx={{ wordBreak: 'break-all' }}>
                  {certificate.issuer}
                </Typography>
              </Grid>
            </Grid>
          </Paper>

          {/* Channels in Use Warning */}
          <ChannelsInUseWarning 
            channelsInUse={certificate.channelsInUse} 
            severity="warning"
            message="Removing this certificate may affect channel configurations. Please ensure channels are updated accordingly."
          />

          {/* Warning Message */}
          <Alert severity="warning" sx={{ mb: 2 }}>
            <Typography variant="body2">
              This action cannot be undone. The certificate will be permanently removed from the {certificate.store} store.
            </Typography>
          </Alert>

          <DialogContentText>
            Are you sure you want to remove this certificate?
          </DialogContentText>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button 
          onClick={handleRemove}
          variant="contained"
          color="error"
          disabled={loading}
        >
          {loading ? 'Removing...' : 'Remove Certificate'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
