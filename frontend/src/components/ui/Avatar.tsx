interface AvatarProps {
  firstName: string;
  lastName: string;
  size?: 'md' | 'lg';
  className?: string;
}

/**
 * Initials on a dark disc. There are no profile pictures in the domain model,
 * so initials are the identity marker rather than a placeholder for an image.
 */
export function Avatar({ firstName, lastName, size = 'md', className = '' }: AvatarProps) {
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();

  return (
    <span
      className={
        `flex ${size === 'lg' ? 'h-11 w-11 text-sm' : 'h-9 w-9 text-xs'} shrink-0 items-center justify-center rounded-full bg-fill-neutral-strong ` +
        `font-semibold text-on-accent ${className}`
      }
      aria-hidden="true"
    >
      {initials}
    </span>
  );
}
