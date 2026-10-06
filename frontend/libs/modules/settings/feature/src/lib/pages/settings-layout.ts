import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Alert } from '@wallet/shared-ui';

import { SettingsFacade } from '../data-access/settings-facade';
import { SETTINGS_SECTIONS } from '../settings-sections';
import { SettingsNav } from '../ui/settings-nav';

/**
 * Il guscio delle impostazioni: titolo, menu laterale, pagina scelta.
 *
 * Fornisce **qui** il facade, e non nelle singole pagine: conti e sorgenti si
 * caricano una volta sola e sopravvivono al passaggio da una scheda all'altra.
 * Se ogni pagina avesse il proprio, tornare su «Conti» dopo aver salvato il
 * profilo rifarebbe due chiamate per riottenere dati che erano già in memoria —
 * e un aggiornamento appena lanciato perderebbe il proprio esito.
 */
@Component({
  selector: 'app-settings-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [SettingsFacade],
  imports: [RouterOutlet, Alert, SettingsNav],
  templateUrl: './settings-layout.html',
  host: { class: 'block' },
})
export class SettingsLayout {
  protected readonly facade = inject(SettingsFacade);
  protected readonly sections = SETTINGS_SECTIONS;
}
