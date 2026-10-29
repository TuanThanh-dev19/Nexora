import { useState } from 'react'
import { Button, Form } from 'react-bootstrap'

export function TicketKeywordSearch({ keyword, onSearch }) {
  const [value, setValue] = useState(keyword)
  const invalid = value.trim().length > 200
  return (
    <Form
      className="mb-3"
      onSubmit={(event) => {
        event.preventDefault()
        if (!invalid) onSearch(value.trim())
      }}
    >
      <Form.Label htmlFor="ticket-keyword">Tìm theo mã hoặc tiêu đề</Form.Label>
      <div className="d-flex gap-2">
        <Form.Control
          id="ticket-keyword"
          type="search"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          isInvalid={invalid}
          aria-describedby={invalid ? 'ticket-keyword-error' : undefined}
        />
        <Button type="submit" disabled={invalid} className="flex-shrink-0">
          Tìm kiếm
        </Button>
      </div>
      {invalid && (
        <p id="ticket-keyword-error" role="alert" className="text-danger small">
          Từ khóa có tối đa 200 ký tự sau khi bỏ khoảng trắng đầu/cuối.
        </p>
      )}
    </Form>
  )
}
