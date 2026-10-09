import { Modal } from '../ui/Modal';
import { RegisterForm } from '../auth/RegisterForm';

interface NewUserModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => Promise<void>;
}

export function NewUserModal({ open, onClose, onCreated }: NewUserModalProps) {
  return (
    <Modal open={open} onClose={onClose} title="Crear usuario">
      <RegisterForm
        allowRoleSelection
        submitLabel="Crear usuario"
        onSuccess={() => {
          void onCreated();
        }}
      />
    </Modal>
  );
}
