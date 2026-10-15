import { useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Form,
  Row,
  Spinner,
  Table,
} from 'react-bootstrap'
import { FiPlus, FiSave, FiTool, FiTrash2 } from 'react-icons/fi'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import {
  PROFICIENCY_LEVELS,
  PROFICIENCY_LABELS,
} from '../constants/technicianConstants'
import { formatTechnicianError, isConflictError } from '../api/technicianError'
import {
  useActiveSkills,
  useUpdateTechnicianSkills,
} from '../hooks/useTechnicians'

export function TechnicianSkillsPanel({
  technician,
  readOnly = false,
  onConflict,
}) {
  const [workingSkills, setWorkingSkills] = useState(() =>
    (technician?.skills ?? []).map((s) => ({
      skillId: Number(s.skillId),
      skillCode: s.skillCode ?? s.code,
      skillName: s.skillName ?? s.name,
      proficiencyLevel: Number(s.proficiencyLevel),
    })),
  )
  const [selectedSkillId, setSelectedSkillId] = useState('')
  const [selectedProficiency, setSelectedProficiency] = useState(3)
  const [feedback, setFeedback] = useState(null)

  const activeSkillsQuery = useActiveSkills()
  const updateMutation = useUpdateTechnicianSkills()

  // Filter skills catalog to exclude already assigned skills
  const availableSkills = (activeSkillsQuery.data ?? []).filter(
    (catalogSkill) =>
      !workingSkills.some((ws) => Number(ws.skillId) === Number(catalogSkill.id)),
  )

  const handleAddSkill = () => {
    if (!selectedSkillId) return
    const skillToAdd = activeSkillsQuery.data?.find(
      (s) => Number(s.id) === Number(selectedSkillId),
    )
    if (!skillToAdd) return

    setWorkingSkills((prev) => [
      ...prev,
      {
        skillId: skillToAdd.id,
        skillCode: skillToAdd.code,
        skillName: skillToAdd.name,
        proficiencyLevel: Number(selectedProficiency),
      },
    ])
    setSelectedSkillId('')
    setSelectedProficiency(3)
  }

  const handleRemoveSkill = (skillId) => {
    setWorkingSkills((prev) => prev.filter((s) => s.skillId !== skillId))
  }

  const handleChangeProficiency = (skillId, newLevel) => {
    setWorkingSkills((prev) =>
      prev.map((s) =>
        s.skillId === skillId
          ? { ...s, proficiencyLevel: Number(newLevel) }
          : s,
      ),
    )
  }

  // Calculate dirty state
  const originalSkills = (technician?.skills ?? []).map((s) => ({
    skillId: Number(s.skillId),
    proficiencyLevel: Number(s.proficiencyLevel),
  }))
  const currentSkills = workingSkills.map((s) => ({
    skillId: Number(s.skillId),
    proficiencyLevel: Number(s.proficiencyLevel),
  }))
  const isDirty =
    JSON.stringify(originalSkills) !== JSON.stringify(currentSkills)

  const handleSave = async () => {
    if (!isDirty || readOnly) return

    setFeedback(null)
    try {
      await updateMutation.mutateAsync({
        technicianId: technician.technicianId,
        payload: {
          skills: workingSkills.map((s) => ({
            skillId: s.skillId,
            proficiencyLevel: s.proficiencyLevel,
          })),
          version: technician.version,
        },
      })
      setFeedback({
        type: 'success',
        message: 'Đã cập nhật danh sách kỹ năng thành công.',
      })
    } catch (err) {
      if (isConflictError(err)) {
        if (onConflict) onConflict(err)
      } else {
        setFeedback({
          type: 'danger',
          message: formatTechnicianError(err),
        })
      }
    }
  }

  return (
    <Card className="shadow-sm mb-4">
      <Card.Header className="d-flex align-items-center justify-content-between py-3">
        <div className="d-flex align-items-center gap-2">
          <FiTool className="fs-5 text-primary" aria-hidden="true" />
          <h2 className="h5 mb-0">Kỹ năng chuyên môn</h2>
        </div>
        <Badge bg="light" text="dark" className="border">
          {workingSkills.length} kỹ năng
        </Badge>
      </Card.Header>

      <Card.Body>
        <p className="text-secondary small mb-3">
          Cấu hình danh mục chuyên môn và mức độ thành thạo phục vụ việc đối chiếu khi phân công xử lý các sự cố kỹ thuật.
        </p>

        {feedback && (
          <Alert
            variant={feedback.type}
            dismissible
            onClose={() => setFeedback(null)}
            className="mb-3"
          >
            {feedback.message}
          </Alert>
        )}

        {/* Add Skill Form (hidden in read-only) */}
        {!readOnly && (
          <div className="bg-light p-3 rounded border mb-4">
            <h3 className="h6 mb-2">Thêm kỹ năng mới</h3>
            {activeSkillsQuery.isPending ? (
              <LoadingSpinner label="Đang tải danh mục kỹ năng..." />
            ) : activeSkillsQuery.isError ? (
              <Alert variant="warning" className="py-2 small">
                Không thể tải danh mục kỹ năng từ máy chủ.{' '}
                <Button
                  variant="link"
                  size="sm"
                  onClick={() => activeSkillsQuery.refetch()}
                  className="p-0 text-decoration-underline"
                >
                  Thử lại
                </Button>
              </Alert>
            ) : (
              <Row className="g-2 align-items-end">
                <Col xs={12} sm={6} md={5}>
                  <Form.Label
                    htmlFor="skill-select"
                    className="small fw-semibold mb-1"
                  >
                    Chọn kỹ năng
                  </Form.Label>
                  <Form.Select
                    id="skill-select"
                    value={selectedSkillId}
                    onChange={(e) => setSelectedSkillId(e.target.value)}
                    disabled={availableSkills.length === 0}
                  >
                    <option value="">
                      {availableSkills.length === 0
                        ? '-- Đã thêm tất cả kỹ năng --'
                        : '-- Chọn kỹ năng --'}
                    </option>
                    {availableSkills.map((skill) => (
                      <option key={skill.id} value={skill.id}>
                        {skill.name} ({skill.code})
                      </option>
                    ))}
                  </Form.Select>
                </Col>

                <Col xs={12} sm={4} md={4}>
                  <Form.Label
                    htmlFor="proficiency-select"
                    className="small fw-semibold mb-1"
                  >
                    Mức độ thành thạo
                  </Form.Label>
                  <Form.Select
                    id="proficiency-select"
                    value={selectedProficiency}
                    onChange={(e) =>
                      setSelectedProficiency(Number(e.target.value))
                    }
                  >
                    {PROFICIENCY_LEVELS.map((lvl) => (
                      <option key={lvl.value} value={lvl.value}>
                        {lvl.label}
                      </option>
                    ))}
                  </Form.Select>
                </Col>

                <Col xs={12} sm={2} md={3}>
                  <Button
                    variant="outline-primary"
                    onClick={handleAddSkill}
                    disabled={!selectedSkillId || updateMutation.isPending}
                    className="w-100 d-inline-flex align-items-center justify-content-center gap-1"
                  >
                    <FiPlus aria-hidden="true" /> Thêm
                  </Button>
                </Col>
              </Row>
            )}
          </div>
        )}

        {/* Skills list / table */}
        {workingSkills.length === 0 ? (
          <EmptyState message="Chưa có kỹ năng nào được cấu hình cho kỹ thuật viên này." />
        ) : (
          <Table responsive hover className="align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th style={{ width: '25%' }}>Mã kỹ năng</th>
                <th style={{ width: '35%' }}>Tên kỹ năng</th>
                <th style={{ width: '25%' }}>Mức độ thành thạo</th>
                {!readOnly && (
                  <th style={{ width: '15%' }} className="text-end">
                    Hành động
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {workingSkills.map((skill) => (
                <tr key={skill.skillId}>
                  <td>
                    <code>{skill.skillCode}</code>
                  </td>
                  <td className="fw-semibold">{skill.skillName}</td>
                  <td>
                    {readOnly ? (
                      <Badge bg="info" className="text-dark">
                        {PROFICIENCY_LABELS[skill.proficiencyLevel] ??
                          `Mức ${skill.proficiencyLevel}`}
                      </Badge>
                    ) : (
                      <Form.Select
                        size="sm"
                        aria-label={`Mức thành thạo cho ${skill.skillName}`}
                        value={skill.proficiencyLevel}
                        disabled={updateMutation.isPending}
                        onChange={(e) =>
                          handleChangeProficiency(
                            skill.skillId,
                            Number(e.target.value),
                          )
                        }
                        style={{ maxWidth: '180px' }}
                      >
                        {PROFICIENCY_LEVELS.map((lvl) => (
                          <option key={lvl.value} value={lvl.value}>
                            {lvl.label}
                          </option>
                        ))}
                      </Form.Select>
                    )}
                  </td>
                  {!readOnly && (
                    <td className="text-end">
                      <Button
                        size="sm"
                        variant="outline-danger"
                        aria-label={`Xóa kỹ năng ${skill.skillName}`}
                        onClick={() => handleRemoveSkill(skill.skillId)}
                        disabled={updateMutation.isPending}
                      >
                        <FiTrash2 aria-hidden="true" /> Xóa
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </Table>
        )}

        {!readOnly && (
          <div className="d-flex justify-content-end mt-4 pt-3 border-top">
            <Button
              variant="primary"
              onClick={handleSave}
              disabled={!isDirty || updateMutation.isPending}
              className="d-inline-flex align-items-center gap-1"
            >
              {updateMutation.isPending ? (
                <>
                  <Spinner size="sm" animation="border" /> Đang lưu...
                </>
              ) : (
                <>
                  <FiSave aria-hidden="true" /> Lưu danh sách kỹ năng
                </>
              )}
            </Button>
          </div>
        )}
      </Card.Body>
    </Card>
  )
}
