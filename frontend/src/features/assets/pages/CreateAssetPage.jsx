import { Card, Container } from 'react-bootstrap'
import { Link, useNavigate } from 'react-router'
import { AssetForm } from '../components/AssetForm'

export function CreateAssetPage() {
  const navigate = useNavigate()

  return (
    <Container className="page-container">
      <Link to="/assets" className="back-link">
        ← Danh sách tài sản
      </Link>

      <div className="page-heading mb-4">
        <div>
          <p className="eyebrow">QUẢN LÝ THIẾT BỊ</p>
          <h1 className="h2 mb-0">Thêm tài sản mới</h1>
          <p className="text-secondary mb-0">
            Nhập thông tin đăng ký tài sản mới vào hệ thống quản lý.
          </p>
        </div>
      </div>

      <Card className="shadow-sm">
        <Card.Body className="p-4">
          <AssetForm
            onCancel={() => navigate('/assets')}
            onSuccess={(savedAsset) => {
              navigate('/assets', {
                state: {
                  notice: `Đã tạo tài sản "${savedAsset.assetCode}" thành công.`,
                },
              })
            }}
          />
        </Card.Body>
      </Card>
    </Container>
  )
}
