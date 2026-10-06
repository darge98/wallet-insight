import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'block shimmer rounded-lg',
    '[style.height.px]': 'height()',
    '[style.width]': 'width()',
    '[attr.aria-hidden]': 'true',
  },
  template: '',
})
export class Skeleton {
  readonly height = input(16);
  readonly width = input('100%');
}
