import React, { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  Paper,
  Grid,
  Alert
} from '@mui/material'
import { useAliasEdit } from '../hooks/useAliasEdit'
import { updateCertificateAlias } from '../services/tlsService'
import ChannelsInUseWarning from './ChannelsInUseWarning'
import ConfirmReplaceCertificateDialog from './ConfirmReplaceCertificateDialog'

export default function EditAliasDialog({ 
  open, 
  onClose, 
  certificate, 
  currentCertificates = null,
  onSuccess 
}) {
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)
  
  const {
    newAlias,
    aliasWarning,
    loading,
    apiError,
    existingCertificateInfo,
    handleAliasChange,
    validate,
    checkAliasExists,
    setLoading,
    setApiError
  } = useAliasEdit(certificate?.alias || '', certificate?.store, currentCertificates)

  const handleSubmit = async () => {
    if (!validate()) return

    // Check if alias already exists
    const aliasExists = checkAliasExists(newAlias)
    if (aliasExists) {
      setShowConfirmDialog(true)
      return
    }

    // Proceed with alias update
    await performAliasUpdate()
  }

  const performAliasUpdate = async () => {
    setLoading(true)
    setApiError(null)
    try {
      const result = await updateCertificateAlias(
        certificate.store, 
        certificate.alias, 
        newAlias,
        currentCertificates
      )
      if (result.success) {
        onSuccess?.(result.data)
        onClose()
      } else {
        setApiError(result.error || 'Failed to update alias')
      }
    } catch (error) {
      setApiError(error.message || 'Failed to update alias')
    } finally {
      setLoading(false)
    }
  }

  const handleConfirmReplace = async () => {
    setShowConfirmDialog(false)
    await performAliasUpdate()
  }

  const handleCancelReplace = () => {
    setShowConfirmDialog(false)
  }

  const handleClose = () => {
    setShowConfirmDialog(false)
    setApiError(null)
    onClose()
  }

  if (!certificate) return null

  return (
    <>
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Certificate Alias</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            {apiError && (
              <Alert severity="error" onClose={() => setApiError(null)} sx={{ mb: 2 }}>
                {apiError}
              </Alert>
            )}

            {/* Channels in Use Warning */}
            <ChannelsInUseWarning 
              channelsInUse={certificate.channelsInUse} 
              severity="warning"
              message="Changing the alias may affect channel configurations. Please ensure channels are updated accordingly."
            />

            {/* Alias Input */}
            <TextField
              label="New Alias"
              value={newAlias}
              onChange={handleAliasChange}
              error={Boolean(apiError)}
              helperText={apiError || aliasWarning || "Enter a unique alias for this certificate"}
              fullWidth
              required
              sx={{ 
                '& .MuiOutlinedInput-root': {
                  '& fieldset': {
                    borderColor: aliasWarning ? '#ff9800' : 'default',
                  },
                  '&:hover fieldset': {
                    borderColor: aliasWarning ? '#ff9800' : 'default',
                  },
                  '&.Mui-focused fieldset': {
                    borderColor: aliasWarning ? '#ff9800' : 'default',
                  },
                },
                '& .MuiFormHelperText-root': {
                  color: aliasWarning ? '#ff9800' : 'inherit'
                }
              }}
            />

            {/* Certificate Info Display */}
            <Paper variant="outlined" sx={{ p: 2, mb: 3, mt: 2 }}>
              <Typography variant="h6" gutterBottom>Certificate Information</Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="body2" color="text.secondary">Current Alias</Typography>
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

            
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={loading}>
            Cancel
          </Button>
          <Button 
            variant="contained" 
            onClick={handleSubmit} 
            disabled={loading}
          >
            {loading ? 'Updating...' : 'Update Alias'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialog for Replacing Existing Certificate */}
      <ConfirmReplaceCertificateDialog
        open={showConfirmDialog}
        onClose={handleCancelReplace}
        onConfirm={handleConfirmReplace}
        alias={newAlias}
        store={certificate?.store}
        loading={loading}
        existingCertificateInfo={existingCertificateInfo}
      />
    </>
  )
}
