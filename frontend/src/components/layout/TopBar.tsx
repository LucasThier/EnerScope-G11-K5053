import { Logo } from '../ui/Logo';
import { ProjectSwitcher } from './ProjectSwitcher';
import { UserMenu } from './UserMenu';

/**
 * The application header: brand on the left, active project and signed-in user
 * on the right. It spans the full width and sits above the sidebar, so the
 * project shown here reads as scoping the whole app rather than one section.
 *
 * The horizontal padding is 24px to match the sidebar's own 12px rail padding
 * plus its 12px row padding, which puts the logo on the same vertical axis as
 * the navigation icons below it.
 */
export function TopBar() {
  return (
    <header className="flex h-16 shrink-0 items-center gap-4 border-b border-ink-100 bg-white px-6">
      <Logo variant="mark" />
      <div className="flex-1" />
      <ProjectSwitcher />
      <div className="h-8 w-px bg-ink-100" aria-hidden="true" />
      <UserMenu />
    </header>
  );
}
