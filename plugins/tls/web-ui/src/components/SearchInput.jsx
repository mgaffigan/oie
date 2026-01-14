import React, { useEffect, useState } from 'react'
import { TextField, InputAdornment } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'

export default function SearchInput({ value, onChange, placeholder = 'Search certificates by alias or subject…', delay = 250 }) {
  const [internal, setInternal] = useState(value ?? '')

  useEffect(() => setInternal(value ?? ''), [value])

  useEffect(() => {
    const id = setTimeout(() => onChange?.(internal), delay)
    return () => clearTimeout(id)
  }, [internal, delay, onChange])

  return (
    <TextField
      value={internal}
      onChange={(e) => setInternal(e.target.value)}
      placeholder={placeholder}
      fullWidth
      size="small"
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" />
            </InputAdornment>
          )
        }
      }}
    />
  )
}


