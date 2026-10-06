import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormField, FormRoot, form, schema, validate } from '@angular/forms/signals';

import { isCompactJwt } from '@wallet/ingestion-domain';

interface TokenModel {
  token: string;
}

const tokenSchema = schema<TokenModel>((path) => {
  validate(path.token, ({ value }) => {
    const token = value().trim();
    if (token === '') return { kind: 'required' };
    return isCompactJwt(token) ? undefined : { kind: 'jwt' };
  });
});

/** Il campo con cui si incolla un token nuovo al posto di quello collegato. */
@Component({
  selector: 'app-token-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, FormRoot],
  host: { class: 'block' },
  template: `
    <form [formRoot]="tokenForm" class="grid gap-2">
      <label class="block text-xs font-medium text-ink-muted" [for]="fieldId()">
        Token personale nuovo
      </label>
      <input
        [id]="fieldId()"
        type="password"
        autocomplete="off"
        spellcheck="false"
        placeholder="eyJhbGciOiJIUzI1NiJ9.…"
        [formField]="tokenForm.token"
        [attr.aria-invalid]="error() !== null"
        [attr.aria-describedby]="fieldId() + '-help'"
        class="field font-mono placeholder:font-sans"
      />
      @if (error() === 'required') {
        <p [id]="fieldId() + '-help'" class="text-xs text-negative">Incolla il token nuovo.</p>
      } @else if (error() === 'format') {
        <p [id]="fieldId() + '-help'" class="text-xs text-negative">Token non valido.</p>
      } @else {
        <p [id]="fieldId() + '-help'" class="text-xs text-ink-faint">
          Wallet → Impostazioni → API.
        </p>
      }
      <div>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ saving() ? 'Importo i movimenti…' : 'Salva token' }}
        </button>
      </div>
    </form>
  `,
})
export class TokenForm {
  readonly fieldId = input.required<string>();
  readonly saving = input(false);

  readonly submitted = output<string>();

  private readonly model = signal<TokenModel>({ token: '' });

  protected readonly tokenForm = form(this.model, tokenSchema, {
    submission: {
      action: async () => {
        this.submitted.emit(this.model().token.trim());
        this.tokenForm().reset({ token: '' });
      },
    },
  });

  protected error(): 'required' | 'format' | null {
    const state = this.tokenForm.token();
    if (!state.touched()) return null;
    if (state.getError('required')) return 'required';
    return state.getError('jwt') ? 'format' : null;
  }
}
