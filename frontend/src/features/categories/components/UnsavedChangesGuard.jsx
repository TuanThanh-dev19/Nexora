import { useEffect } from 'react'
import { useBlocker } from 'react-router'
import { Button, Modal } from 'react-bootstrap'

export function UnsavedChangesGuard({ dirty }) {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty &&
      currentLocation.pathname + currentLocation.search !==
        nextLocation.pathname + nextLocation.search,
  )
  useEffect(() => {
    const guard = (event) => {
      if (dirty) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [dirty])
  return (
    <Modal
      show={blocker.state === 'blocked'}
      onHide={() => blocker.reset?.()}
      centered
    >
      <Modal.Header closeButton>
        <Modal.Title>Rời trang khi chưa lưu?</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        Thay đổi chưa lưu sẽ bị mất. Bạn có muốn rời trang?
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={() => blocker.reset()}>
          Tiếp tục chỉnh sửa
        </Button>
        <Button variant="danger" onClick={() => blocker.proceed()}>
          Bỏ thay đổi và rời trang
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
