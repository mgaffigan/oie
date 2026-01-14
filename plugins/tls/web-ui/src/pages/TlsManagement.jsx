import React, { useMemo, useState } from 'react'
import { Box, Paper, Dialog, DialogTitle, DialogContent, DialogActions, Button } from '@mui/material'
import { useSearchParams } from 'react-router-dom'
import TabsWithCounts from '../components/TabsWithCounts'
import TabPanel from '../components/TabPanel'
import StoreToolbar from '../components/StoreToolbar'
import SearchInput from '../components/SearchInput'
import CertificateList from '../components/CertificateList'
import useCertificates from '../hooks/useCertificates'
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import VpnKeyIcon from '@mui/icons-material/VpnKey'
import ImportTrustedCertificateDialog from '../components/ImportTrustedCertificateDialog'
import ImportPrivateCertificateDialog from '../components/ImportPrivateCertificateDialog'
import ImportFromUrlDialogContent from '../components/ImportFromUrlDialogContent'
import ImportCertificateChainDialogContent from '../components/ImportCertificateChainDialogContent'
import CertificateDetailsDialog from '../components/CertificateDetailsDialog'
import EditAliasDialog from '../components/EditAliasDialog'
import RemoveCertificateDialog from '../components/RemoveCertificateDialog'
import { useNotification } from '../context/NotificationContext'

export default function TlsManagement() {
  const [params, setParams] = useSearchParams()
  const tabKeys = ['native', 'trusted', 'private']
  const urlTab = params.get('tab')
  // Derive tabKey directly from URL - single source of truth, no state needed
  const tabKey = useMemo(() => {
    return urlTab && tabKeys.includes(urlTab) ? urlTab : 'native'
  }, [urlTab, tabKeys])
  
  const { all, counts, filterBy, loading, error, refetch, getCertificatesByStore } = useCertificates(tabKey)
  const { showSuccess, showError } = useNotification()
  const [search, setSearch] = useState('')

  const [dialogOpen, setDialogOpen] = useState(false)
  const [dialogTitle, setDialogTitle] = useState('')
  const [dialogType, setDialogType] = useState(null) // 'text' | 'import-certificate' | null
  const [dialogProps, setDialogProps] = useState({})
  
  const [detailsDialogOpen, setDetailsDialogOpen] = useState(false)
  const [selectedCertificate, setSelectedCertificate] = useState(null)
  const [showPrivateKeys, setShowPrivateKeys] = useState(false)
  
  const [editAliasDialogOpen, setEditAliasDialogOpen] = useState(false)
  const [certificateToEdit, setCertificateToEdit] = useState(null)
  
  const [removeDialogOpen, setRemoveDialogOpen] = useState(false)
  const [certificateToRemove, setCertificateToRemove] = useState(null)

  const openDialog = ({ type, title, props = {} }) => {
    setDialogTitle(title)
    setDialogType(type)
    setDialogProps(props)
    setDialogOpen(true)
  }

  const closeDialog = () => {
    setDialogOpen(false)
    setDialogType(null)
    setDialogProps({})
  }

  const handleImportSuccess = () => {
    // Refresh the certificate data after successful import
    refetch()
    closeDialog()
  }

  const handleViewDetails = (certificate) => {
    setSelectedCertificate(certificate)
    setDetailsDialogOpen(true)
  }

  const handleCloseDetails = () => {
    setDetailsDialogOpen(false)
    setSelectedCertificate(null)
  }

  const handleTogglePrivateKeys = () => {
    setShowPrivateKeys(!showPrivateKeys)
  }

  const handleExport = (certificate) => {
    // TODO: Implement certificate export functionality
    console.log('Export certificate:', certificate)
  }

  const handleEditAlias = (certificate) => {
    setCertificateToEdit(certificate)
    setEditAliasDialogOpen(true)
  }

  const handleCloseEditAlias = () => {
    setEditAliasDialogOpen(false)
    setCertificateToEdit(null)
  }

  const handleAliasEditSuccess = () => {
    // Refresh the certificate data after successful alias edit
    refetch()
    handleCloseEditAlias()
  }

  const handleRemove = (certificate) => {
    setCertificateToRemove(certificate)
    setRemoveDialogOpen(true)
  }

  const handleCloseRemove = () => {
    setRemoveDialogOpen(false)
    setCertificateToRemove(null)
  }

  const handleRemoveSuccess = () => {
    // Refresh the certificate data after successful removal
    refetch()
    handleCloseRemove()
    showSuccess(`Certificate "${certificateToRemove?.alias}" has been removed successfully`)
  }

  const openImportDialog = () => {
    const targetStore = tabKey === 'trusted' ? 'trusted' : 'private'
    if (targetStore === 'trusted') {
      // Use certificate chain import for trusted store
      openDialog({ type: 'import-certificate-chain', title: 'Import Certificate Chain', props: { targetStore } })
    } else {
      // Use regular import for private store
      openDialog({ type: 'import-certificate', title: 'Import Key Pair (PEM)', props: { targetStore } })
    }
  }

  const onTabChange = (_e, newIndex) => {
    const newKey = tabKeys[newIndex]
    setSearch('')
    setParams((prev) => {
      const p = new URLSearchParams(prev)
      p.set('tab', newKey)
      return p
    }, { replace: true })
    // tabKey will automatically update from URL via useMemo
  }

  const tabIndex = useMemo(() => Math.max(0, tabKeys.indexOf(tabKey)), [tabKey])
  const visibleRows = useMemo(() => filterBy(tabKey, search), [filterBy, tabKey, search])

  const tabs = [
    { key: 'native', label: 'Native Java Certificate Store', count: counts.native, icon: <ShieldOutlinedIcon fontSize="small" /> },
    { key: 'trusted', label: 'Additional Trusted Certificates', count: counts.trusted, icon: <CheckCircleOutlineIcon fontSize="small" /> },
    { key: 'private', label: 'Local Key Pairs', count: counts.private, icon: <VpnKeyIcon fontSize="small" /> },
  ]

  const toolbarByTab = {
    native: {
      title: 'Native Java Certificate Store',
      warning: 'Read-only system store',
      actions: [],
    },
    trusted: {
      title: 'Additional Trusted Certificates',
      actions: [
        { key: 'import', label: 'Import Certificate', color: 'info', onClick: () => openImportDialog() },
        { key: 'import-url', label: 'Import from URL', color: 'info', onClick: () => openDialog({ type: 'import-from-url', title: 'Import Certificate from URL', props: { targetStore: 'trusted' } }) },

      ],
    },
    private: {
      title: 'Local Key Pairs',
      actions: [
        { key: 'show-private-keys', label: showPrivateKeys ? 'Hide Private Keys' : 'Show Private Keys', color: 'warning', onClick: handleTogglePrivateKeys },
        { key: 'import-cert', label: 'Import Key Pair', color: 'info', onClick: () => openImportDialog() },

      ],
    },
  }

  return (
    <Box>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <TabsWithCounts value={tabIndex} onChange={onTabChange} tabs={tabs} />
      </Paper>

      <TabPanel value={tabIndex} index={0} sx={{ mt: 2 }}>
        <StoreToolbar title={toolbarByTab.native.title} warning={toolbarByTab.native.warning} actions={toolbarByTab.native.actions} />
        <SearchInput value={search} onChange={setSearch} />
        <Box sx={{ mt: 2 }}>
          <CertificateList 
            rows={visibleRows} 
            loading={loading} 
            error={error} 
            onViewDetails={handleViewDetails}
            onExport={handleExport}
            onEditAlias={handleEditAlias}
            onRemove={handleRemove}
            showPrivateKeys={showPrivateKeys}
          />
        </Box>
      </TabPanel>

      <TabPanel value={tabIndex} index={1} sx={{ mt: 2 }}>
        <StoreToolbar title={toolbarByTab.trusted.title} actions={toolbarByTab.trusted.actions} />
        <SearchInput value={search} onChange={setSearch} />
        <Box sx={{ mt: 2 }}>
          <CertificateList 
            rows={visibleRows} 
            loading={loading} 
            error={error} 
            onViewDetails={handleViewDetails}
            onExport={handleExport}
            onEditAlias={handleEditAlias}
            onRemove={handleRemove}
            showPrivateKeys={showPrivateKeys}
          />
        </Box>
      </TabPanel>

      <TabPanel value={tabIndex} index={2} sx={{ mt: 2 }}>
        <StoreToolbar title={toolbarByTab.private.title} actions={toolbarByTab.private.actions} />
        <SearchInput value={search} onChange={setSearch} />
        <Box sx={{ mt: 2 }}>
          <CertificateList 
            rows={visibleRows} 
            loading={loading} 
            error={error} 
            onViewDetails={handleViewDetails}
            onExport={handleExport}
            onEditAlias={handleEditAlias}
            onRemove={handleRemove}
            showPrivateKeys={showPrivateKeys}
          />
        </Box>
      </TabPanel>

      <Dialog open={dialogOpen} onClose={closeDialog} fullWidth maxWidth="lg">
        <DialogTitle>{dialogTitle}</DialogTitle>
        <DialogContent>
          {dialogType === 'import-certificate-chain' && dialogProps.targetStore === 'trusted' && (
            <ImportCertificateChainDialogContent
              targetStore={dialogProps.targetStore}
              currentCertificates={getCertificatesByStore('trusted')}
              onCancel={closeDialog}
              onSuccess={handleImportSuccess}
            />
          )}
          {dialogType === 'import-certificate' && dialogProps.targetStore === 'private' && (
            <ImportPrivateCertificateDialog
              currentCertificates={getCertificatesByStore('private')}
              onCancel={closeDialog}
              onSubmit={() => closeDialog()}
              onSuccess={handleImportSuccess}
            />
          )}
          {dialogType === 'import-from-url' && (
            <ImportFromUrlDialogContent
              targetStore={dialogProps.targetStore}
              currentCertificates={getCertificatesByStore(dialogProps.targetStore)}
              onCancel={closeDialog}
              onSuccess={handleImportSuccess}
            />
          )}
          {dialogType === 'text' && (
            <Box sx={{ pt: 0.5 }}>{dialogProps.text}</Box>
          )}
        </DialogContent>
        {dialogType === 'text' && (
          <DialogActions>
            <Button onClick={closeDialog}>Close</Button>
          </DialogActions>
        )}
      </Dialog>

      <CertificateDetailsDialog
        open={detailsDialogOpen}
        onClose={handleCloseDetails}
        certificate={selectedCertificate}
      />

      <EditAliasDialog
        open={editAliasDialogOpen}
        onClose={handleCloseEditAlias}
        certificate={certificateToEdit}
        currentCertificates={certificateToEdit ? getCertificatesByStore(certificateToEdit.store) : null}
        onSuccess={handleAliasEditSuccess}
      />

      <RemoveCertificateDialog
        open={removeDialogOpen}
        onClose={handleCloseRemove}
        certificate={certificateToRemove}
        currentCertificates={certificateToRemove ? getCertificatesByStore(certificateToRemove.store) : null}
        onSuccess={handleRemoveSuccess}
      />

    </Box>
  )
}
