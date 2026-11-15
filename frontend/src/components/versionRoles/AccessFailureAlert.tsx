import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';

interface AccessFailureAlertProps {
  message: string | null;
  onRetry: () => void;
}

export function AccessFailureAlert({ message, onRetry }: AccessFailureAlertProps) {
  if (!message) {
    return null;
  }
  return (
    <Alert tone="error" className="mb-4">
      <span>{message}</span>
      <Button variant="ghost" onClick={onRetry}>
        Reintentar
      </Button>
    </Alert>
  );
}
