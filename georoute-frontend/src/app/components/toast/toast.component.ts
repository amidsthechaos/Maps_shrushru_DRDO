import { Component } from '@angular/core';
import { MapStateService } from '../../services/map-state.service';

@Component({
  selector: 'app-toast',
  templateUrl: './toast.component.html',
  styleUrls: ['./toast.component.scss'],
})
export class ToastComponent {
  constructor(public mapState: MapStateService) {}
}
