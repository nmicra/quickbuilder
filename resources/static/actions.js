Vue.component('test-reactor-pom',{
    props: ['chosenenv'],
    data: function() {
        return {
            changes: ''
        }
    },
    methods: {
            testPom: function(){
                console.log(this.changes)
                Vue.http.get('/test/'+this.chosenenv, {headers: {'changes': this.changes}}).then(result =>
                       result.text().then(dataResult => {
                        this.$root.$children[0].myAlert = dataResult;
                        this.$bvModal.show('pathto-modal');
                       }))
            }
        },
    template: `<b-card no-body class="mb-1">
                <b-card-header header-tag="header" class="p-1" role="tab">
                  <b-button block href="#" v-b-toggle.accordion-4 variant="info">Test generated Reactor POM</b-button>
                </b-card-header>
                <b-collapse id="accordion-4" visible accordion="my-accordion" role="tabpanel">
                  <b-card-body>
                    <b-card-text>You can test here, which POM will be generated for a given changes.
                        <code>Please NOTE: this utility does not considers any build states</code>
                    </b-card-text>
                    <div>
                        <b-form-textarea
                          id="textarea"
                          v-model="changes"
                          placeholder="Enter comma separated changes. Eg: disparko/disparko-server/src/main/kotlin/com/disparko/security/JWTAuthorizationFilter.kt,disparko/disparko-server/src/main/kotlin/com/disparko/parking/ParkingLotsData.kt"
                          rows="4"></b-form-textarea>
                      </div>
                    <b-button variant="outline-primary" @click="testPom()">Check</b-button>
                  </b-card-body>
                </b-collapse>
              </b-card>`
});

Vue.component('path-from',{
    props: ['chosenenv'],
    data: function() {
        return {
            selected: null,
            options: []
        }
    },
    created: function(){

        if (this.options.length <= 0){
            Vue.http.get('/allmodules/'+this.chosenenv).then(result =>
                        result.text().then(dataResult => {
                                      dataResult.split(',').forEach(a => this.options.push(a));
                                }
                        )
                    )
        }

    },
    methods: {
            pathfrom: function(selectedModule){
                Vue.http.get('/pathfrom/'+this.chosenenv+'/' + selectedModule).then(result =>
                       result.text().then(dataResult => {
                        this.$root.$children[0].myAlert = dataResult;
                        this.$bvModal.show('pathto-modal');
                       }))
            }
        },
    template: `<b-card no-body class="mb-1">
                <b-card-header header-tag="header" class="p-1" role="tab">
                  <b-button block href="#" v-b-toggle.accordion-3 variant="info">Path From</b-button>
                </b-card-header>
                <b-collapse id="accordion-3" visible accordion="my-accordion" role="tabpanel">
                  <b-card-body>
                    <b-card-text>Click the <code>module</code> below, and find out which modules you can reach <code>from</code> the selected module</b-card-text>
                    <b-form-select v-model="selected" :options="options" :select-size="4"></b-form-select>
                    <div class="mt-3">Selected: <strong><code>{{ selected }}</code></strong></div>
                    <b-button variant="outline-primary" @click="pathfrom(selected)">Check</b-button>
                  </b-card-body>
                </b-collapse>
              </b-card>`
});

Vue.component('path-to',{
    props: ['chosenenv'],
    data: function() {
        return {
            selected: null,
            options: []
        }
    },
    created: function(){

        if (this.options.length <= 0){
            Vue.http.get('/allmodules/'+this.chosenenv).then(result =>
                        result.text().then(dataResult => {
                                      dataResult.split(',').forEach(a => this.options.push(a));
                                }
                        )
                    )
        }

    },
    methods: {
            pathto: function(selectedModule){
                Vue.http.get('/pathto/'+this.chosenenv+'/' + selectedModule).then(result =>
                       result.text().then(dataResult => {
                        this.$root.$children[0].myAlert = dataResult;
                        this.$bvModal.show('pathto-modal');
                       }))
            }
        },
    template: `<b-card no-body class="mb-1">
                <b-card-header header-tag="header" class="p-1" role="tab">
                  <b-button block href="#" v-b-toggle.accordion-2 variant="info">Path To</b-button>
                </b-card-header>
                <b-collapse id="accordion-2" visible accordion="my-accordion" role="tabpanel">
                  <b-card-body>
                    <b-card-text>Click the <code>module</code> below, and find out from which modules you can reach it.</b-card-text>
                    <b-form-select v-model="selected" :options="options" :select-size="4"></b-form-select>
                    <div class="mt-3">Selected: <strong><code>{{ selected }}</code></strong></div>
                    <b-button variant="outline-primary" @click="pathto(selected)">Check</b-button>
                  </b-card-body>
                </b-collapse>
              </b-card>`
});

Vue.component('rebuild-metadata',{
    props: ['chosenenv'],
    data: function() {
            return {
                dismissSecs: 10,
                dismissCountDown: 0,
                waitFlag: false,
                alertMsg: ''
            }
        },
    methods: {
                countDownChanged(dismissCountDown) {
                    this.dismissCountDown = dismissCountDown
                },

                rebuildmetadata: function(chosenenv){
                    this.waitFlag = true;
                    Vue.http.get('/rebuildmetadata/'+chosenenv).then(result =>
                           result.text().then(dataResult => {
                            this.waitFlag = false;
                            this.alertMsg = dataResult;
                            this.dismissCountDown=10;
                           }))
                }
            },
    template: `<b-card no-body class="mb-1">
                <b-card-header header-tag="header" class="p-1" role="tab">
                  <b-button block href="#" v-b-toggle.accordion-1 variant="info">Rebuild Metadata</b-button>
                </b-card-header>
                <b-collapse id="accordion-1" visible accordion="my-accordion" role="tabpanel">
                  <b-card-body>
                    <b-card-text>Rebuild metadata for <code>{{ chosenenv }}</code> env.</b-card-text>
                    <b-button variant="outline-primary" @click="rebuildmetadata(chosenenv)">Rebuild</b-button>
                    <b-alert :show="waitFlag">Wait ...</b-alert>
                     <b-alert :show="dismissCountDown" dismissible variant="warning" @dismissed="dismissCountDown=0" @dismiss-count-down="countDownChanged">
                        <p>{{alertMsg}}   [{{ dismissCountDown }}]</p>
                        <b-progress variant="warning" :max="dismissSecs" :value="dismissCountDown" height="4px"></b-progress>
                     </b-alert>
                  </b-card-body>
                </b-collapse>
              </b-card>`
});


Vue.component('quickbuilder-actions',{
    props: ['chosenenv'],
    template: `<div role="tablist">
                   <rebuild-metadata :chosenenv="chosenenv"/>
                   <path-to :chosenenv="chosenenv"/>
                   <path-from :chosenenv="chosenenv"/>
                   <test-reactor-pom :chosenenv="chosenenv"/>
                 </div>`
});

Vue.component('quickbuilder-envs',{
    props: ['envs'],
    data: function() {
            return {
                myAlert: 'My message'
            }
        },
    template: `<div>
                    <b-card no-body>
                        <b-tabs pills card vertical>
                            <b-tab v-for="env in envs" :title="env"><quickbuilder-actions :chosenenv="env"/></b-tab>
                        </b-tabs>
                    </b-card>
                    <b-modal id="pathto-modal" title="Your result">{{myAlert}}</b-modal>
                </div>`
});


var app = new Vue({
    el: '#app',
    template: `<quickbuilder-envs :envs="envs"/>`,
    data: {
        envs: ['Development', 'Master']
    }
});
