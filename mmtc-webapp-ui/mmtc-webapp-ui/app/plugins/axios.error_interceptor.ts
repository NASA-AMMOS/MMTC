import axios from 'axios';

export default defineNuxtPlugin(() => {
  const toast = useToast()

  axios.interceptors.response.use(
    (response) => {
      return response;
    },
    (error: AxiosError) => {
      let message = error.response?.data?.message ||
        error.response?.data?.error ||
        error.response?.data ||
        error.message ||
        "An error occurred.";

      message += "  Please see MMTC log for details."

      toast.add({
        title: 'Error',
        description: message,
        color: 'error',
        duration: 15000
      });

      return Promise.reject(error);
    }
  )
});
