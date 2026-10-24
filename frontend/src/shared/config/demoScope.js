// Feature availability is independent of MSW. Permissions still come from role/state policies.
export const demoScope = {
  actions: {
    review: true,
    assign: true,
    start: true,
    addWorkLog: true,
    resolve: true,
    close: true,
    reject: false,
    cancel: false,
    reassign: false,
    unassign: false,
    reopen: false,
    forceClose: false,
    uploadWorkResult: false,
  },
  features: {
    myWork: true,
    workLogs: true,
    attachmentRead: true,
    dashboard: true,
    comments: false,
    feedback: false,
  },
}
