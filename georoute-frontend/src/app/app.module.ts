import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { MapComponent } from './components/map/map.component';
import { RoutePanelComponent } from './components/route-panel/route-panel.component';
import { ToastComponent } from './components/toast/toast.component';

@NgModule({
  declarations: [AppComponent, MapComponent, RoutePanelComponent, ToastComponent],
  imports: [
    BrowserModule,
    HttpClientModule, // all requests go to localhost
    FormsModule,
    ReactiveFormsModule,
    CommonModule,
    AppRoutingModule,
  ],
  providers: [],
  bootstrap: [AppComponent],
})
export class AppModule {}
