import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbDate } from '@ng-bootstrap/ng-bootstrap';
import { NotifierService } from 'angular-notifier';
import { environment } from 'src/environments/environment';
import { DiscordGuildConfig } from '../model/discord-guild-config';
import { DiscordObject } from '../model/discord-object';
import { DiscordUserLog } from '../model/discord-user-log';
import { UtilsService } from '../utils.service';

@Component({
  selector: 'app-discord',
  templateUrl: './discord.component.html',
  styleUrls: ['./discord.component.css']
})
export class DiscordComponent implements OnInit {

  tab = 'logs';
  loadingUserLogs = false;
  loadingBotConfig = false;
  loadingChannels = false;
  loadingRoles = false;
  updatingConfig = false;

  userLogList: DiscordUserLog[];
  pagedUserLogList: DiscordUserLog[];
  guildConfig: DiscordGuildConfig;
  guildRoleList: DiscordObject[];
  guildChannelList: DiscordObject[];

  displayName = '';
  fromDate: any;
  toDate:any;
  action = '';

  selectedWelcomeChannelName = '';
  selectedBirthdayChannelName = '';
  
  selectedWelcomeRoleName = '';
  selectedBirthdayRoleName = '';

  currentPage = -1;
  totalPages = 0;
  totalLogs = 0;
  resultPerPage = 15;
  math = Math;

  constructor(private http: HttpClient, private title: Title, public utils: UtilsService, private activatedRoute: ActivatedRoute,
      private router: Router, private notifierService: NotifierService) {
    this.title.setTitle('Discord Insights');
    let tab = this.activatedRoute.snapshot.queryParamMap.get('tab');
    this.tab = tab == null ? 'logs' : tab;
  }

  ngOnInit() {
    this.onTabSelected(this.tab);
  }

  onTabSelected(newTab: string) {
    this.tab = newTab;
    if (this.tab == 'logs') {
      this.loadUserLogs(0);
    } else {
      this.loadBotConfig();
    }

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { tab: newTab },
      queryParamsHandling: 'merge'
    });
  }

  loadUserLogs(page: number) {
    if (page < 0 || (page > this.totalPages && this.totalPages != -1)) return;
    this.loadingUserLogs = true;
    this.currentPage = page;

    let queryParam = new HttpParams().set('limit', this.resultPerPage.toString())
      .set('offset', (this.resultPerPage * this.currentPage).toString());
    if (this.displayName.trim() != '') {
      queryParam = queryParam.set('keyword', this.displayName.trim());
    }
    if (this.action != '') {
      queryParam = queryParam.set('action', this.action);
    }
    if (this.fromDate != null) {
      queryParam = queryParam.set('from', new Date(this.fromDate.year + '-' + this.fromDate.month + '-' + this.fromDate.day).toISOString());
    }
    if (this.toDate != null) {
      queryParam = queryParam.set('to', new Date(this.toDate.year + '-' + this.toDate.month + '-' + this.toDate.day).toISOString());
    }

    const httpOptions = {
      params: queryParam,
      observe: 'response' as 'response'
    };
    this.http.get<DiscordUserLog[]>(environment.urlPrefix + 'api/discord/default/user-logs', httpOptions).subscribe(res => {
      this.userLogList = res.body;
      this.pagedUserLogList = res.body;
      this.totalLogs = Number(res.headers.get('X-Total-Count'));
      this.totalPages = Math.ceil(Number(res.headers.get('X-Total-Count')) / this.resultPerPage - 1);
      this.loadingUserLogs = false;
    }, error => {
      this.loadingUserLogs = false;
      console.log(error.error);
    });
  }

  loadBotConfig() {
    this.loadingBotConfig = true;
    this.loadingChannels = true;
    this.loadingRoles = true;
    this.guildConfig = null;
    this.guildChannelList = null;
    this.guildRoleList = null;

    this.http.get<DiscordGuildConfig>(environment.urlPrefix + 'api/discord/default/config').subscribe(guildConfig => {
      this.guildConfig = guildConfig;
      this.processSelectedDropdowns();
      this.loadingBotConfig = false;
    }, error => {
      this.loadingBotConfig = false;
      console.log(error.error);
    });

    this.http.get<DiscordObject[]>(environment.urlPrefix + 'api/discord/default/channels').subscribe(guildChannelList => {
      this.guildChannelList = guildChannelList;
      this.processSelectedDropdowns();
      this.loadingChannels = false;
    }, error => {
      this.loadingChannels = false;
      console.log(error.error);
    });

    this.http.get<DiscordObject[]>(environment.urlPrefix + 'api/discord/default/roles').subscribe(guildRoleList => {
      this.guildRoleList = guildRoleList;
      this.processSelectedDropdowns();
      this.loadingRoles = false;
    }, error => {
      this.loadingRoles = false;
      console.log(error.error);
    });
  }

  processSelectedDropdowns() {
    if (this.guildConfig != null && this.guildChannelList != null && this.guildRoleList != null) {
      this.guildChannelList.unshift(new DiscordObject({id: null, name: 'Disable channel message'}))
      this.guildRoleList.unshift(new DiscordObject({id: null, name: 'Disable role assignment'}))

      if (this.guildConfig.welcomeChannelId == null) {
        this.selectedWelcomeChannelName = this.guildChannelList[0].name;
      } else {
        this.selectedWelcomeChannelName = this.guildChannelList.find(c => c.id == this.guildConfig.welcomeChannelId).name;
      }

      if (this.guildConfig.birthdayChannelId == null) {
        this.selectedBirthdayChannelName = this.guildChannelList[0].name;
      } else {
        this.selectedBirthdayChannelName = this.guildChannelList.find(c => c.id == this.guildConfig.birthdayChannelId).name;
      }

      if (this.guildConfig.welcomeRoleId == null) {
        this.selectedWelcomeRoleName = this.guildRoleList[0].name;
      } else {
        this.selectedWelcomeRoleName = this.guildRoleList.find(r => r.id == this.guildConfig.welcomeRoleId).name;
      }

      if (this.guildConfig.birthdayRoleId == null) {
        this.selectedBirthdayRoleName = this.guildRoleList[0].name;
      } else {
        this.selectedBirthdayRoleName = this.guildRoleList.find(r => r.id == this.guildConfig.birthdayRoleId).name;
      }
    }
  }

  onActionSelected(action: string) {
    this.action = action;
    this.loadUserLogs(0);
  }

  onWelcomeChannelSelected(option: DiscordObject) {
    this.selectedWelcomeChannelName = option.name;
    this.guildConfig.welcomeChannelId = option.id;
  }

  onBirthdayChannelSelected(option: DiscordObject) {
    this.selectedBirthdayChannelName = option.name;
    this.guildConfig.birthdayChannelId = option.id;
  }

  onWelcomeRoleSelected(option: DiscordObject) {
    this.selectedWelcomeRoleName = option.name;
    this.guildConfig.welcomeRoleId = option.id;
  }

  onBirthdayRoleSelected(option: DiscordObject) {
    this.selectedBirthdayRoleName = option.name;
    this.guildConfig.birthdayRoleId = option.id;
  }

  onSaveChanges() {
    this.updatingConfig = true;
    this.http.post<DiscordGuildConfig>(environment.urlPrefix + 'api/discord/default/config', this.guildConfig).subscribe(() => {
      this.notifierService.notify('success', 'Changes saved successfully.');
      this.updatingConfig = false;
    }, error => {
      this.updatingConfig = false;
      this.notifierService.notify('error', error);
    });
  }

}
