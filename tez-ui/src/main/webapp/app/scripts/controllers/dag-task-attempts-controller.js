/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

App.DagTaskAttemptsController = Em.ObjectController.extend(App.PaginatedContentMixin, App.ColumnSelectorMixin, {
  // Required by the PaginatedContentMixin
  needs: 'dag',
  childEntityType: 'taskAttempt',

  controllerName: 'DagTaskAttemptsController',

  queryParams: {
    status_filter: 'status',
    vertex_name_filter: 'vertex_name',
  },
  status_filter: null,
  vertex_name_filter: null,

  loadData: function() {
    var primaryFilter = {
      TEZ_DAG_ID : this.get('controllers.dag.id')
    };
    if (!!this.vertex_name_filter) {
      var vertexIdMap = this.get('controllers.dag.vertexIdToNameMap');
      var vertexId = App.Helpers.misc.getVertexIdFromName(vertexIdMap, this.vertex_name_filter)
        || 'unknown';
      primaryFilter = { TEZ_VERTEX_ID : vertexId };
    }

    var filters = {
      primary: primaryFilter,
      secondary: {
        status: this.status_filter
      }
    }
    this.setFiltersAndLoadEntities(filters);
  },

  load: function () {
    var dag = this.get('controllers.dag.model'),
        controller = this.get('controllers.dag'),
        t = this;
    t.set('loading', true);
    dag.reload().then(function () {
      return controller.loadAdditional(dag);
    }).then(function () {
      t.resetNavigation();
      t.loadEntities();
    }).catch(function(error){
      Em.Logger.error(error);
      var err = App.Helpers.misc.formatError(error, defaultErrMsg);
      var msg = 'error code: %@, message: %@'.fmt(err.errCode, err.msg);
      App.Helpers.ErrorBar.getInstance().show(msg, err.details);
    });
  }.observes('count'),

  actions : {
    filterUpdated: function(filterID, value) {
      // any validations required goes here.
      if (!!value) {
        this.set(filterID, value);
      } else {
        this.set(filterID, null);
      }
      this.loadData();
    }
  },

  updateLoading: function () {
    var dagController = this.get('controllers.dag'),
        model = this.get('controllers.dag.model'),
        that = this,
        dagStatus = that.get('controllers.dag.status');

    dagController.loadAdditional(model).then(function () {
      that.get('entities').forEach(function (attempt) {

        var attemptStatus = App.Helpers.misc
          .getFixedupDisplayStatus(attempt.get('status'));
        if (attemptStatus == 'RUNNING' &&
          App.Helpers.misc.isStatusInUnsuccessful(dagStatus)) {
          attemptStatus = 'KILLED'
        }
        if (attemptStatus != attempt.get('status')) {
          attempt.set('status', attemptStatus);
        }
      });

      that.set('loading', false);
    });
  },

  defaultColumnConfigs: function() {
    var that = this,
        vertexIdToNameMap = this.get('controllers.dag.vertexIdToNameMap') || {};
    return [
      {
        id: 'taskId',
        headerCellName: 'Task Index',
        tableCellViewClass: Em.Table.TableCell.extend({
          template: Em.Handlebars.compile(
            "{{#link-to 'task' view.cellContent.taskId class='ember-table-content'}}{{view.cellContent.displayId}}{{/link-to}}")
        }),
        getCellContent: function (row) {
          var taskId = row.get('taskID'),
              idPrefix = 'task_%@_'.fmt(row.get('dagID').substr(4));
          return {
            taskId: taskId,
            displayId: taskId.indexOf(idPrefix) == 0 ? taskId.substr(idPrefix.length) : taskId
          };
        }
      },
      {
        id: 'attemptNo',
        headerCellName: 'Attempt No',
        tableCellViewClass: Em.Table.TableCell.extend({
          template: Em.Handlebars.compile(
            "{{#link-to 'taskAttempt' view.cellContent.attemptID class='ember-table-content'}}{{view.cellContent.attemptNo}}{{/link-to}}")
        }),
        getCellContent: function(row) {
          var attemptID = row.get('id') || '',
              attemptNo = attemptID.split(/[_]+/).pop();
          return {
            attemptNo: attemptNo,
            attemptID: attemptID
          };
        }
      },
      {
        id: 'vertexName',
        headerCellName: 'Vertex Name',
        filterID: 'vertex_name_filter',
        getCellContent: function(row) {
          var vertexId = row.get('vertexID');
          return vertexIdToNameMap[vertexId] || vertexId;
        }
      },
      {
        id: 'startTime',
        headerCellName: 'Start Time',
        getCellContent: function(row) {
          return App.Helpers.date.dateFormat(row.get('startTime'));
        }
      },
      {
        id: 'endTime',
        headerCellName: 'End Time',
        getCellContent: function(row) {
          return App.Helpers.date.dateFormat(row.get('endTime'));
        }
      },
      {
        id: 'duration',
        headerCellName: 'Duration',
        getCellContent: function(row) {
          var st = row.get('startTime');
          var et = row.get('endTime');
          if (st && et) {
            return App.Helpers.date.durationSummary(st, et);
          }
        }
      },
      {
        id: 'status',
        headerCellName: 'Status',
        filterID: 'status_filter',
        filterType: 'dropdown',
        dropdownValues: App.Helpers.misc.taskAttemptStatusUIOptions,
        tableCellViewClass: Em.Table.TableCell.extend({
          template: Em.Handlebars.compile(
            '<span class="ember-table-content">&nbsp;\
            <i {{bind-attr class=":task-status view.cellContent.statusIcon"}}></i>\
            &nbsp;&nbsp;{{view.cellContent.status}}</span>')
        }),
        getCellContent: function(row) {
          var status = row.get('status');
          return {
            status: status,
            statusIcon: App.Helpers.misc.getStatusClassForEntity(status)
          };
        }
      },
      {
        id: 'containerId',
        headerCellName: 'Container',
        contentPath: 'containerId'
      },
      {
        id: 'nodeId',
        headerCellName: 'Node',
        contentPath: 'nodeId'
      },
      {
        id: 'actions',
        headerCellName: 'Actions',
        tableCellViewClass: Em.Table.TableCell.extend({
          template: Em.Handlebars.compile(
            '<span class="ember-table-content">\
            {{#link-to "taskAttempt.counters" view.cellContent}}counters{{/link-to}}&nbsp;\
            </span>'
            )
        }),
        contentPath: 'id'
      },
      {
        id: 'logs',
        headerCellName: 'Logs',
        tableCellViewClass: Em.Table.TableCell.extend({
          template: Em.Handlebars.compile(
            '<span class="ember-table-content">\
              {{#unless view.cellContent.notAvailable}}\
                Not Available\
              {{else}}\
                {{#if view.cellContent.viewUrl}}\
                  <a target="_blank" href="//{{unbound view.cellContent.viewUrl}}">View</a>\
                  &nbsp;\
                {{/if}}\
                {{#if view.cellContent.downloadUrl}}\
                  <a target="_blank" href="{{unbound view.cellContent.downloadUrl}}?start=0" download type="application/octet-stream">Download</a>\
                {{/if}}\
              {{/unless}}\
            </span>')
        }),
        getCellContent: function(row) {
          var yarnAppState = that.get('controllers.dag.yarnAppState'),
              suffix = "/syslog_" + row.get('id'),
              link = row.get('inProgressLog') || row.get('completedLog'),
              cellContent = {};

          if(link) {
            cellContent.viewUrl = link + suffix;
          }
          link = row.get('completedLog');
          if (link && yarnAppState === 'FINISHED' || yarnAppState === 'KILLED' || yarnAppState === 'FAILED') {
            cellContent.downloadUrl = link + suffix;
          }

          cellContent.notAvailable = cellContent.viewUrl || cellContent.downloadUrl;

          return cellContent;
        }
      }
    ];
  }.property(),

  columnConfigs: function() {
    return this.get('defaultColumnConfigs').concat(
      App.Helpers.misc.normalizeCounterConfigs(
        App.get('Configs.defaultCounters').concat(
          App.get('Configs.tables.entity.taskAttempt') || [],
          App.get('Configs.tables.sharedColumns') || []
        )
      )
    );
  }.property(),

});
