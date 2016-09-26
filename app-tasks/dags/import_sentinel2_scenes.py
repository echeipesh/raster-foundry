from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG
from datetime import datetime, timedelta

from rf.uploads.sentinel2 import create_sentinel2_scenes

import logging

rf_logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
rf_logger.addHandler(ch)

logger = logging.getLogger(__name__)


seven_days_ago = datetime.combine(
        datetime.today() - timedelta(7), datetime.min.time())


args = {
    'owner': 'raster-foundry',
    'start_date': seven_days_ago,
}


dag = DAG(
    dag_id='import_sentinel2_scenes',
    default_args=args,
    schedule_interval=None
)


def import_sentinel2(*args, **kwargs):
    """Creates new sentinel 2 scenes with associated images, thumbnails, and footprint"""
    conf = kwargs['dag_run'].conf
    tilepath = conf.get('tilepath')
    logger.info("Importing Scenes from tile path %s...".format(tilepath))
    create_sentinel2_scenes(tilepath)
    return "Finished importing scenes for tilepath {}".format(tilepath)


sentinel2_importer = PythonOperator(
    task_id='import_sentinel2',
    python_callable=import_sentinel2,
    provide_context=True,
    dag=dag
)
