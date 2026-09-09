interface AvatarProps {
  firstName: string;
  lastName: string;
  className?: string;
}

/**
 * Initials on a dark disc. There are no profile pictures in the domain model,
 * so initials are the identity marker rather than a placeholder for an image.
 */
export function Avatar({ firstName, lastName, className = '' }: AvatarProps) {
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();

  return (
    <span
      className={
        'flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-ink-800 ' +
        `text-xs font-semibold text-white ${className}`
      }
      aria-hidden="true"
    >
      {initials}
    </span>
  );
}
