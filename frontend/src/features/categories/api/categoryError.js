export const errorMessage = (error) =>
  error?.response?.data?.detail ??
  error?.message ??
  'Something went wrong. Please retry.'
