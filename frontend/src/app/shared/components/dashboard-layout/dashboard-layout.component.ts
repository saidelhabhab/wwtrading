import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';

@Component({
  selector: 'wwt-dashboard-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-layout.component.html',
  styleUrl: './dashboard-layout.component.scss'
})
export class DashboardLayoutComponent {
  @Input() title = 'Tableau de bord';
  @Output() menuStateChange = new EventEmitter<boolean>();
  protected sidebarOpen = false;

  protected toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
    this.menuStateChange.emit(this.sidebarOpen);
  }

  protected closeSidebar(): void {
    this.sidebarOpen = false;
    this.menuStateChange.emit(false);
  }
}
