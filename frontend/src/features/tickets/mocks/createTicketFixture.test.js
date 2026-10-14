// @vitest-environment node
import { expect, it } from 'vitest'
import { createTicketPreviewApi } from './createTicketFixture'

function multipart(title) {
  const data = new FormData()
  data.append(
    'ticket',
    new Blob(
      [
        JSON.stringify({
          categoryId: 1,
          formVersionId: 501,
          locationId: 1,
          title,
          description: 'Description',
          dynamicValues: [
            { fieldKey: 'field2', value: 0 },
            { fieldKey: 'field7', value: false },
          ],
          attachments: [{ fileIndex: 0, fieldKey: 'field8' }],
        }),
      ],
      { type: 'application/json' },
    ),
  )
  data.append('files', new File(['image'], 'linked.png', { type: 'image/png' }))
  return data
}

it('keeps distinct submitted snapshots for multiple mock tickets', async () => {
  const api = createTicketPreviewApi({ delay: 0 })
  const first = await api.submit(multipart('First'))
  const second = await api.submit(multipart('Second'))
  expect(second.id).not.toBe(first.id)
  expect(second.ticketNumber).not.toBe(first.ticketNumber)
  expect(api.getTicket(first.id).title).toBe('First')
  expect(api.getTicket(second.id).title).toBe('Second')
  expect(api.getTicket(first.id).dynamicValues).toEqual([
    { fieldKey: 'field2', value: 0 },
    { fieldKey: 'field7', value: false },
  ])
  expect(api.getTicket(first.id).files[0].name).toBe('linked.png')
})
it('does not retain a failed mutation and exposes a newer form after conflict', async () => {
  const api = createTicketPreviewApi({ delay: 0 })
  api.setScenario('409')
  await expect(api.submit(multipart('Failed'))).rejects.toMatchObject({
    response: { status: 409 },
  })
  expect(api.getTicket(9001)).toBeUndefined()
  expect(await api.form()).toMatchObject({ id: 502, versionNumber: 2 })
  api.setScenario('success')
  expect(await api.submit(multipart('Successful'))).toMatchObject({ id: 9001 })
})
