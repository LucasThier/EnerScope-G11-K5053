import { useState, type FormEvent } from 'react';
import { Card } from '../components/ui/Card';
import { TextField } from '../components/ui/TextField';
import { Button } from '../components/ui/Button';
import { Alert } from '../components/ui/Alert';
import { useOrganizations } from '../hooks/useOrganizations';
import { getErrorMessage } from '../api/errors';

/** Admin page: create organizations and see the existing ones. */
export function AdminOrganizationsPage() {
  const { organizations, loading, error, createOrganization } = useOrganizations();
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [created, setCreated] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) return;
    setCreateError(null);
    setCreated(null);
    setCreating(true);
    try {
      const org = await createOrganization(name.trim());
      setCreated(`${org.name} was created.`);
      setName('');
    } catch (err) {
      setCreateError(getErrorMessage(err, 'Could not create the organization'));
    } finally {
      setCreating(false);
    }
  }

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-700">Organizations</h1>
        <p className="mt-1 text-sm text-ink-400">Create organizations and review the existing ones.</p>
      </header>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="text-lg font-semibold text-ink-700">Create organization</h2>
          <form onSubmit={handleCreate} className="mt-4 flex flex-col gap-4" noValidate>
            {createError && <Alert tone="error">{createError}</Alert>}
            {created && <Alert tone="success">{created}</Alert>}
            <TextField
              label="Name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Acme Energy"
            />
            <Button type="submit" loading={creating} className="w-full">
              Create organization
            </Button>
          </form>
        </Card>

        <Card>
          <h2 className="text-lg font-semibold text-ink-700">Existing organizations</h2>
          <div className="mt-4">
            {loading ? (
              <p className="text-sm text-ink-400">Loading…</p>
            ) : error ? (
              <Alert tone="error">{error}</Alert>
            ) : organizations.length === 0 ? (
              <p className="text-sm text-ink-400">No organizations yet.</p>
            ) : (
              <ul className="divide-y divide-ink-100">
                {organizations.map((org) => (
                  <li key={org.id} className="py-2.5 text-sm text-ink-700">
                    {org.name}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}
