import React from 'react'
import { Tabs, Tab, Chip, Box, Stack } from '@mui/material'

export default function TabsWithCounts({ value, onChange, tabs }) {
  return (
    <Tabs value={value} onChange={onChange} variant="fullWidth" sx={{ width: '100%' }}>
      {tabs.map((tab, index) => (
        <Tab key={tab.key}
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              {tab.icon ? <Box sx={{ display: 'flex', color: 'inherit' }}>{tab.icon}</Box> : null}
              <Box component="span" sx={{ fontWeight: value === index ? 600 : 500 }}>{tab.label}</Box>
              <Chip label={tab.count ?? 0} size="small" color={value === index ? 'primary' : 'default'} variant={value === index ? 'filled' : 'outlined'} sx={{ height: 22 }} />
            </Stack>
          }
          value={index}
        />
      ))}
    </Tabs>
  )
}


