import { Link, type LinkProps } from 'react-router-dom';
import { buttonClasses, type ButtonSize, type ButtonVariant } from './buttonClasses';

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

/**
 * A router link that looks like a `Button`, same variants and sizes: for an
 * action that is a navigation (it has an address, opens in a new tab, shows in
 * the status bar) and so must be an `<a>`, not a `<button>`.
 */
export function ButtonLink({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...rest
}: ButtonLinkProps) {
  return (
    <Link className={`${buttonClasses(variant, size)} ${className}`} {...rest}>
      {children}
    </Link>
  );
}
