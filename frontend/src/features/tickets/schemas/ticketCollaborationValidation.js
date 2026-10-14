export const COMMENT_MAX_LENGTH = 2000
export const FEEDBACK_COMMENT_MAX_LENGTH = 1000

export function validateComment(payload, editing = false) {
  const errors = {}
  if (
    typeof payload.content !== 'string' ||
    !payload.content.trim() ||
    payload.content.length > COMMENT_MAX_LENGTH
  )
    errors.content = `Nội dung phải có từ 1 đến ${COMMENT_MAX_LENGTH} ký tự.`
  if (!editing && !['PUBLIC', 'INTERNAL'].includes(payload.visibility))
    errors.visibility = 'Chọn phạm vi bình luận hợp lệ.'
  return errors
}

export function validateFeedback(payload) {
  const errors = {}
  if (
    !Number.isInteger(payload.rating) ||
    payload.rating < 1 ||
    payload.rating > 5
  )
    errors.rating = 'Vui lòng chọn mức đánh giá từ 1 đến 5.'
  if (
    payload.comment != null &&
    (typeof payload.comment !== 'string' ||
      payload.comment.length > FEEDBACK_COMMENT_MAX_LENGTH)
  )
    errors.comment = `Nhận xét tối đa ${FEEDBACK_COMMENT_MAX_LENGTH} ký tự.`
  return errors
}
