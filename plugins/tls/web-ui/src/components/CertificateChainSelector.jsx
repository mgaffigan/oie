import React from 'react'
import {
  Box,
  RadioGroup,
  Radio,
  FormControlLabel,
  FormControl,
  Typography,
  Alert,
  Paper
} from '@mui/material'

export default function CertificateChainSelector({
  certificates = [],
  selectedIndex = null,
  onSelect,
  loading = false
}) {
  if (certificates.length === 0) {
    return null
  }

  return (
    <Box sx={{ 
      flex: '0 0 auto',
      pb: 2,
      borderBottom: '1px solid',
      borderColor: 'divider',
      overflow: 'auto',
    }}>
      <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600 }}>
        Select a certificate to import:
      </Typography>

      <FormControl component="fieldset" sx={{ width: '100%' }}>
        <RadioGroup
          value={selectedIndex !== null ? selectedIndex.toString() : ''}
          onChange={(e) => onSelect(parseInt(e.target.value, 10))}
          sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}
        >
          {certificates.map((cert, index) => (
            <Paper
              key={index}
              variant="outlined"
              sx={{
                p: 0.75,
                borderRadius: 1.5,
                cursor: 'pointer',
                transition: 'all 0.2s',
                '&:hover': {
                  backgroundColor: 'action.hover'
                },
                ...(selectedIndex === index && {
                  borderColor: 'primary.main',
                  borderWidth: 2,
                  backgroundColor: 'action.selected'
                })
              }}
              onClick={() => onSelect(index)}
            >
              <FormControlLabel
                value={index.toString()}
                control={<Radio size="small" />}
                label={
                  <Box sx={{ width: '100%', ml: 0.5 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {cert.alias || `Certificate ${index + 1}`}
                    </Typography>
                    {cert.error && (
                      <Alert severity="warning" sx={{ mt: 0.5, py: 0.25, fontSize: '0.75rem' }}>
                        {cert.subject || cert.error}
                      </Alert>
                    )}
                  </Box>
                }
                sx={{ margin: 0, width: '100%' }}
              />
            </Paper>
          ))}
        </RadioGroup>
      </FormControl>
    </Box>
  )
}

