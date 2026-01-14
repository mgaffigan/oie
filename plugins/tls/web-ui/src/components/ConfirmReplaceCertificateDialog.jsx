import React from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Typography,
  Paper,
  Grid
} from '@mui/material'
import ChannelsInUseWarning from './ChannelsInUseWarning'

/**
 * A reusable confirmation dialog for replacing an existing certificate.
 * Shows details of the certificate that will be replaced when existingCertificateInfo is provided.
 * 
 * @param {boolean} open - Dialog visibility
 * @param {function} onClose - Cancel handler
 * @param {function} onConfirm - Confirm handler
 * @param {string} alias - The alias being replaced
 * @param {string} [store] - Store name for the message (optional)
 * @param {boolean} loading - Loading state for button
 * @param {object} [existingCertificateInfo] - Object with { alias, subject, issuer } to show details panel
 */
export default function ConfirmReplaceCertificateDialog({
  open,
  onClose,
  onConfirm,
  alias,
  store,
  loading = false,
  existingCertificateInfo = null
}) {
  const storeText = store ? ` in the ${store} store` : ''
  
  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby="confirm-replace-dialog-title"
      aria-describedby="confirm-replace-dialog-description"
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle id="confirm-replace-dialog-title">
        Replace Existing Certificate
      </DialogTitle>
      <DialogContent>
        <DialogContentText id="confirm-replace-dialog-description" sx={{ mb: 2 }}>
          A certificate with the alias "{alias}" already exists{storeText}. This will replace the existing certificate. Are you sure you want to continue?
        </DialogContentText>
        
        {existingCertificateInfo && (
          <>
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'rgba(255, 152, 0, 0.1)', mb: 2 }}>
              <Typography variant="subtitle2" gutterBottom color="warning.dark">
                Certificate that will be replaced:
              </Typography>
              <Grid container spacing={1}>
                <Grid item xs={12}>
                  <Typography variant="body2" color="text.secondary">Alias</Typography>
                  <Typography variant="body1">{existingCertificateInfo.alias}</Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="body2" color="text.secondary">Subject</Typography>
                  <Typography variant="body1" sx={{ wordBreak: 'break-all' }}>
                    {existingCertificateInfo.subject}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="body2" color="text.secondary">Issuer</Typography>
                  <Typography variant="body1" sx={{ wordBreak: 'break-all' }}>
                    {existingCertificateInfo.issuer}
                  </Typography>
                </Grid>
              </Grid>
            </Paper>
            
            <ChannelsInUseWarning 
              channelsInUse={existingCertificateInfo.channelsInUse} 
              severity="warning"
              message="Replacing this certificate may affect channel configurations. Please ensure channels are updated accordingly."
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          Cancel
        </Button>
        <Button 
          onClick={onConfirm} 
          variant="contained" 
          color="warning"
          disabled={loading}
        >
          {loading ? 'Replacing...' : 'Replace Certificate'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

