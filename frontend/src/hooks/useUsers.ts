import { useCallback, useEffect, useState } from 'react';
import { usersApi } from '../api/users';
import { getErrorMessage } from '../api/errors';
import type { UserListItem } from '../types/auth';

interface UseUsers {
  users: UserListItem[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

export function useUsers(): UseUsers {
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await usersApi.list();
      setUsers(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar los usuarios'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { users, loading, error, reload };
}
