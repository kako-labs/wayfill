const test=require('node:test');
const assert=require('node:assert/strict');
const model=require('../web/pleos-scenario-state.js');

test('happy path ends at the confirmed refuel response without a redundant record screen',()=>{
  let state=model.create();
  for(const action of ['start','depart','recommend','guide','navigation-success','arrive','refuel']){
    state=model.act(state,action);
  }
  assert.equal(state.step,'refueling');
  assert.equal(state.outcome,'refuel-confirmed-full');
  assert.equal(model.act(state,'complete'),state);
});

test('confirmed refuel variants stay distinct so each parked motion can be reviewed',()=>{
  const arrived=model.select(model.create(),'arrived');
  assert.equal(model.act(arrived,'refuel-small').outcome,'refuel-confirmed-small');
  assert.equal(model.act(arrived,'refuel-full').outcome,'refuel-confirmed-full');
  assert.equal(model.act(arrived,'refuel-recovery').outcome,'refuel-confirmed-recovery');
});

test('HUN actions are accepted only while the exact HUN is active',()=>{
  const driving=model.select(model.create(),'driving');
  assert.equal(model.act(driving,'guide'),driving);
  const hun=model.act(driving,'recommend');
  assert.equal(model.act(hun,'later').step,'driving');
  assert.equal(model.act(hun,'later').outcome,'deferred-15m');
  assert.equal(model.act(hun,'expire').outcome,'expired-no-action');
});

test('unknown inputs can block every stage without inventing an action',()=>{
  const unknown=model.act(model.select(model.create(),'routing'),'unknown');
  assert.equal(unknown.step,'unknown');
  assert.equal(model.current(unknown).motion,'unknown');
  assert.equal(model.current(unknown).surface,'split');
});

test('scenario navigation remains bounded',()=>{
  assert.equal(model.previous(model.create()).step,'parked');
  let state=model.create('unknown');
  assert.equal(model.next(state).step,'unknown');
  assert.equal(model.select(state,'missing'),state);
});
