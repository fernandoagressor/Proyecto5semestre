import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      @switch (name()) {
        @case ('dashboard') { <path d="M4 13h6V4H4v9Zm0 7h6v-5H4v5Zm10 0h6v-9h-6v9Zm0-16v5h6V4h-6Z" /> }
        @case ('projects') { <path d="M3 21V5a2 2 0 0 1 2-2h9v18H3Zm11 0V9h5a2 2 0 0 1 2 2v10h-7ZM7 7h3v2H7V7Zm0 4h3v2H7v-2Zm0 4h3v2H7v-2Zm9-2h2v2h-2v-2Z" /> }
        @case ('inventory') { <path d="m12 2 9 5-9 5-9-5 9-5Zm-7.5 8.4L11 14v7.3l-7-3.9v-7.3l.5.3Zm15 0 .5-.3v7.3l-7 3.9V14l6.5-3.6Z" /> }
        @case ('crm') { <path d="M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM6.5 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM16 13c-3 0-5.5 1.5-5.5 3.5V21H22v-4.5c0-2-3-3.5-6-3.5ZM6.5 14C3.5 14 1 15.5 1 17.5V21h7.5v-4.5c0-.9.4-1.7 1.1-2.4-.9-.1-2-.1-3.1-.1Z" /> }
        @case ('sales') { <path d="M6 2h9l4 4v16H6a2 2 0 0 1-2-2V4c0-1.1.9-2 2-2Zm8 1.5V7h3.5L14 3.5ZM8 11v2h7v-2H8Zm0 4v2h7v-2H8Z" /> }
        @case ('wallet') { <path d="M3 5h16a2 2 0 0 1 2 2v2h-6a4 4 0 0 0 0 8h6v2a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7c0-1.1.9-2 2-2Zm12 6h7v4h-7a2 2 0 1 1 0-4Zm0 1.3a.7.7 0 1 0 0 1.4.7.7 0 0 0 0-1.4Z" /> }
        @case ('service') { <path d="M12 2C6.5 2 2 5.8 2 10.5c0 2.6 1.4 4.9 3.6 6.5L4 22l5-2.7c1 .3 2 .5 3 .5 5.5 0 10-3.8 10-9.3S17.5 2 12 2Zm-5 9.5h10v2H7v-2Zm0-4h10v2H7v-2Z" /> }
        @case ('management') { <path d="M3 20V10h4v10H3Zm7 0V4h4v16h-4Zm7 0v-7h4v7h-4Z" /> }
        @case ('audit') { <path d="m12 2 8 3v6c0 5.1-3.4 9.4-8 11-4.6-1.6-8-5.9-8-11V5l8-3Zm-1 13.2 5.2-5.2-1.4-1.4-3.8 3.8-1.8-1.8L7.8 12l3.2 3.2Z" /> }
        @case ('admin') { <path d="M12 2a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm-7 9h14a2 2 0 0 1 2 2v7H3v-7c0-1.1.9-2 2-2Zm6 2v5h2v-5h-2Z" /> }
        @case ('menu') { <path d="M3 6h18v2H3V6Zm0 5h18v2H3v-2Zm0 5h18v2H3v-2Z" /> }
        @case ('close') { <path d="m6.4 5 5.6 5.6L17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19 5 17.6l5.6-5.6L5 6.4 6.4 5Z" /> }
        @case ('sun') { <path d="M11 1h2v3h-2V1Zm0 19h2v3h-2v-3ZM1 11h3v2H1v-2Zm19 0h3v2h-3v-2ZM4.2 3l2.1 2.1-1.4 1.4-2.1-2.1L4.2 3Zm14.9 14.5 2.1 2.1-1.4 1.4-2.1-2.1 1.4-1.4ZM19.8 3l1.4 1.4-2.1 2.1-1.4-1.4L19.8 3ZM4.9 17.5l1.4 1.4L4.2 21l-1.4-1.4 2.1-2.1ZM12 6a6 6 0 1 1 0 12 6 6 0 0 1 0-12Z" /> }
        @case ('moon') { <path d="M20.5 15.3A9 9 0 0 1 8.7 3.5 9 9 0 1 0 20.5 15.3Z" /> }
        @case ('logout') { <path d="M10 3H4a2 2 0 0 0-2 2v14c0 1.1.9 2 2 2h6v-2H4V5h6V3Zm5.6 4.6L14.2 9l2 2H8v2h8.2l-2 2 1.4 1.4L20 12l-4.4-4.4Z" /> }
        @case ('lock') { <path d="M17 9h-1V7a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2Zm-7-2a2 2 0 1 1 4 0v2h-4V7Zm3 10.7V19h-2v-1.3a2 2 0 1 1 2 0Z" /> }
        @case ('bell') { <path d="M12 22a2.4 2.4 0 0 0 2.3-2h-4.6a2.4 2.4 0 0 0 2.3 2Zm7-5v-5a7 7 0 0 0-5-6.7V4a2 2 0 0 0-4 0v1.3A7 7 0 0 0 5 12v5l-2 2h18l-2-2Z" /> }
        @case ('search') { <path d="M10 3a7 7 0 1 1-4.4 12.4L2 19l1.4 1.4L7 16.8A7 7 0 0 1 10 3Zm0 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z" /> }
        @case ('chevron') { <path d="m9 5 7 7-7 7-1.4-1.4 5.6-5.6-5.6-5.6L9 5Z" /> }
        @case ('arrow') { <path d="m12 4 1.4 1.4L8.8 10H20v2H8.8l4.6 4.6L12 18l-7-7 7-7Z" /> }
        @case ('plus') { <path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5Z" /> }
        @case ('location') { <path d="M12 2a8 8 0 0 0-8 8c0 5.5 8 12 8 12s8-6.5 8-12a8 8 0 0 0-8-8Zm0 11a3 3 0 1 1 0-6 3 3 0 0 1 0 6Z" /> }
        @case ('calendar') { <path d="M7 2h2v2h6V2h2v2h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6c0-1.1.9-2 2-2h3V2Zm13 8H4v10h16V10Z" /> }
        @case ('warning') { <path d="M1 21h22L12 2 1 21Zm12-3h-2v-2h2v2Zm0-4h-2v-4h2v4Z" /> }
        @case ('refresh') { <path d="M17.7 6.3A8 8 0 1 0 20 12h-2a6 6 0 1 1-1.8-4.3L13 11h8V3l-3.3 3.3Z" /> }
        @default { <circle cx="12" cy="12" r="9" /> }
      }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; width: 1.25rem; height: 1.25rem; flex: none; }
    svg { width: 100%; height: 100%; fill: currentColor; }
  `,
})
export class AppIconComponent {
  readonly name = input.required<string>();
}
