import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Card, Skeleton } from '@wallet/shared-ui';

import { SettingsFacade } from '../data-access/settings-facade';
import { ProfileForm } from '../ui/profile-form';

/** Chi sei: nome, contatti e le preferenze con cui l'applicazione ti si presenta. */
@Component({
  selector: 'app-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Card, Skeleton, ProfileForm],
  host: { class: 'block' },
  template: `
    <app-card heading="Profilo">
      @if (facade.profile(); as profile) {
        <app-profile-form
          [profile]="profile"
          [saving]="facade.isSavingProfile()"
          [saved]="facade.profileSaved()"
          (save)="facade.saveProfile($event)"
        />
      } @else {
        <app-skeleton class="h-40 w-full" />
      }
    </app-card>
  `,
})
export class ProfilePage {
  protected readonly facade = inject(SettingsFacade);
}
